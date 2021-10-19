package org.wikidata.query.rdf.tool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.wikidata.query.rdf.tool.change.ChangeSourceContext.buildChangeSource;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.IdleConnectionEvictor;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.query.rdf.common.uri.UrisScheme;
import org.wikidata.query.rdf.tool.change.Change.Batch;
import org.wikidata.query.rdf.tool.change.Change.Source;
import org.wikidata.query.rdf.tool.change.ChangeSourceContext;
import org.wikidata.query.rdf.tool.options.OptionsUtils;
import org.wikidata.query.rdf.tool.options.OptionsUtils.WikibaseOptions;
import org.wikidata.query.rdf.tool.options.UpdateOptions;
import org.wikidata.query.rdf.tool.rdf.Munger;
import org.wikidata.query.rdf.tool.rdf.RDFParserSuppliers;
import org.wikidata.query.rdf.tool.rdf.RdfRepository;
import org.wikidata.query.rdf.tool.rdf.client.RdfClient;
import org.wikidata.query.rdf.tool.utils.FileStreamDumper;
import org.wikidata.query.rdf.tool.utils.NullStreamDumper;
import org.wikidata.query.rdf.tool.utils.StreamDumper;
import org.wikidata.query.rdf.tool.wikibase.WikibaseRepository;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import com.github.rholder.retry.Retryer;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import de.thetaphi.forbiddenapis.SuppressForbidden;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressWarnings({"checkstyle:classfanoutcomplexity", "checkstyle:IllegalCatch"})  // this class needs to be split
@SuppressForbidden  // direct use of System.out() / err() should be replaced by a proper logger
public final class WbStackUpdate {

    private static final String USER_AGENT = "WBStack - Query Service - Updater";
    private static final Integer defaultTimeout = 10 * 1000;

    // Static configuration, primarily from environment variables
    private static String wbStackApiEndpoint;
    private static long wbStackSleepBetweenApiCalls;
    private static int wbStackUpdaterThreadCount;
    private static String wbStackUpdaterNamespaces;
    private static String wbStackWikibaseScheme;
    private static int wbStackLoopLimit;
    private static String wbStackProxyMapIngress;

    // Globally reused services and objects
    private static Gson gson;
    private static Properties buildProps;
    private static Closer metricsCloser;
    private static MetricRegistry metricRegistry;

    private static final Logger log = LoggerFactory.getLogger(org.wikidata.query.rdf.tool.WbStackUpdate.class);
    private static final long MAX_FORM_CONTENT_SIZE = Long.getLong("RDFRepositoryMaxPostSize", 200000000L);

    private WbStackUpdate() {
        // utility class, should never be constructed
    }

    private static void setValuesFromEnvOrDie() {
        if (System.getenv("WBSTACK_API_ENDPOINT") == null
                || System.getenv("WBSTACK_BATCH_SLEEP") == null
                || System.getenv("WBSTACK_LOOP_LIMIT") == null) {
            System.err.println("WBSTACK_API_ENDPOINT, WBSTACK_BATCH_SLEEP and WBSTACK_LOOP_LIMIT environment variables must be set.");
            System.exit(1);
        }

        wbStackProxyMapIngress = System.getenv("WBSTACK_PROXYMAP_INGRESS");
        wbStackApiEndpoint = System.getenv("WBSTACK_API_ENDPOINT");
        wbStackSleepBetweenApiCalls = Long.parseLong(System.getenv("WBSTACK_BATCH_SLEEP"));
        wbStackUpdaterThreadCount = Integer.parseInt(System.getenv().getOrDefault("WBSTACK_THREAD_COUNT", "10"));
        wbStackUpdaterNamespaces = System.getenv().getOrDefault("WBSTACK_UPDATER_NAMESPACES", "120,122,146");
        wbStackWikibaseScheme = System.getenv().getOrDefault("WBSTACK_WIKIBASE_SCHEME", "https");
        wbStackLoopLimit = Integer.parseInt(System.getenv("WBSTACK_LOOP_LIMIT"));
    }

    private static void setSingleUseServicesAndObjects() {
        gson = new GsonBuilder().setPrettyPrinting().create();
        buildProps = loadBuildProperties();
        metricsCloser = Closer.create();
        metricRegistry = createMetricRegistry(metricsCloser, "wdqs-updater");
    }

    private static void closeSingleUseServicesAndObjects() throws IOException {
        metricsCloser.close();
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        setValuesFromEnvOrDie();
        setSingleUseServicesAndObjects();

        int loopCount = 0;
        long apiLastCalled;

        Runtime runtime = Runtime.getRuntime();
        while (loopCount < wbStackLoopLimit) {
            loopCount++;
            apiLastCalled = System.currentTimeMillis();
            getAndProcessBatchesFromApi();
            long memory = runtime.totalMemory() - runtime.freeMemory();
            int activeThreads = ManagementFactory.getThreadMXBean().getThreadCount();
            System.out.println(
                    "Loop " + loopCount + "/" + wbStackLoopLimit + ". " +
                            "TotalM: " + (runtime.totalMemory() / (1024L * 1024L)) + ". " +
                            "FreeM: " + (runtime.freeMemory() / (1024L * 1024L)) + ". " +
                            "UsedM: " + (memory / (1024L * 1024L)) + ". " +
                            "MaxM: " + (runtime.maxMemory() / (1024L * 1024L)) + ". " +
                            "Threads: " + activeThreads
            );
            sleepForRemainingTimeBetweenApiCalls(apiLastCalled);
        }

        System.out.println("Finished " + loopCount + " loops. Exiting...");

        closeSingleUseServicesAndObjects();
    }

    private static void sleepForRemainingTimeBetweenApiCalls(long loopLastStarted) throws InterruptedException {
        long secondsRunning = (System.currentTimeMillis() - loopLastStarted) / 1000;
        if (secondsRunning < wbStackSleepBetweenApiCalls) {
            TimeUnit.SECONDS.sleep(wbStackSleepBetweenApiCalls - secondsRunning);
        }
    }

    private static void getAndProcessBatchesFromApi() {
        try {
            JsonArray batches = getBatchesFromApi();
            for (JsonElement batchElement : batches) {
                updateBatch(batchElement);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static JsonArray getBatchesFromApi() throws IOException {
        URL obj = new URL(wbStackApiEndpoint);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);
        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Got non 200 response code from API: " + responseCode);
        }

        JsonElement batches = null;
        try (JsonReader reader = new JsonReader(new InputStreamReader(con.getInputStream(), UTF_8))) {
            batches = gson.fromJson(reader, JsonElement.class);
            if (batches == null) {
                System.err.println("Failed to get JsonArray from JsonReader (returning empty array)");
                return new JsonArray();
            }
            return batches.getAsJsonArray();
        } catch (JsonSyntaxException e) {
            System.err.println("Failed to get JSON from JsonReader, invalid JSON Syntax (returning empty array)");
            return new JsonArray();
        } finally {
            con.disconnect();
        }
    }

    private static void updateBatch(JsonElement batchElement) {
        // Get the values for the batch from the JSON
        JsonObject batch = batchElement.getAsJsonObject();
        String entityIDs = batch.get("entityIds").getAsString();
        JsonObject wiki = batch.get("wiki").getAsJsonObject();
        String domain = wiki.get("domain").getAsString();
        JsonObject wikiQsNamespace = wiki.get("wiki_queryservice_namespace").getAsJsonObject();
        String qsBackend = wikiQsNamespace.get("backend").getAsString();
        String qsNamespace = wikiQsNamespace.get("namespace").getAsString();

        // Run the main Update class with our altered args
        runUpdaterWithArgs(new String[]{
                "--wikibaseHost", domain,
                "--ids", entityIDs,
                "--entityNamespaces", wbStackUpdaterNamespaces,
                "--sparqlUrl", "http://" + qsBackend + "/bigdata/namespace/" + qsNamespace + "/sparql",
                "--wikibaseScheme", wbStackWikibaseScheme,
                "--conceptUri", "http://" + domain
        });

        // TODO on success maybe report back?
    }

    @SuppressFBWarnings(value = "IMC_IMMATURE_CLASS_PRINTSTACKTRACE", justification = "We should introduce proper logging framework")
    private static void runUpdaterWithArgs(String[] args) {
        try {
            Closer closer = Closer.create();

            try {
                log.info("Starting Updater {} ({}) {}",
                        buildProps.getProperty("git.build.version", "UNKNOWN"),
                        buildProps.getProperty("git.commit.id", "UNKNOWN"),
                        String.join(" ", args));
                Updater<? extends Batch> updater = initialize(args, closer);
                try {
                    updater.run();
                } catch (Exception var2) {
                    log.error("Error during updater run.", var2);
                    throw var2;
                }
            } catch (Throwable var7) {
                throw closer.rethrow(var7);
            } finally {
                closer.close();
            }

        } catch (Exception e) {
            System.err.println("Failed batch!");
            e.printStackTrace();
        }
    }

    private static String getProxyMapString( UpdateOptions options ) {
        if ( wbStackProxyMapIngress == null ) {
            return null;
        }

        return options.wikibaseHost() + "=" + wbStackProxyMapIngress;
    }

    private static Updater<? extends Batch> initialize(String[] args, Closer closer) throws URISyntaxException {
        try {
            UpdateOptions options = (UpdateOptions) OptionsUtils.handleOptions(UpdateOptions.class, args);

            // Don't use the ConnectionManager from HttpClientUtils as it constantly spawns Eviction threads that won't stop
            PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(-1, TimeUnit.SECONDS);
            poolingConnectionManager.setDefaultMaxPerRoute(100);
            poolingConnectionManager.setMaxTotal(100);
            poolingConnectionManager.setDefaultSocketConfig(SocketConfig.copy(SocketConfig.DEFAULT)
                    .setSoTimeout(defaultTimeout)
                    .build());
            closer.register(poolingConnectionManager);

            IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(poolingConnectionManager, 1L, TimeUnit.SECONDS);
            connectionEvictor.start();
            closer.register(new ClosableIdleConnectionEvictor(connectionEvictor));

            // CloseableHttpClient that is closed by WikibaseRepository.close, which is registered to the closer
            CloseableHttpClient httpClientApache = HttpClientUtils.createHttpClient(
                    poolingConnectionManager,
                    null,
                    getProxyMapString( options ), // ex: platform-nginx.default.svc.cluster.local:8080",
                    defaultTimeout
            );

            WikibaseRepository wikibaseRepository = new WikibaseRepository(
                    UpdateOptions.uris(options),
                    options.constraints(),
                    metricRegistry,
                    createStreamDumper(UpdateOptions.dumpDirPath(options)),
                    UpdateOptions.revisionDuration(options),
                    RDFParserSuppliers.defaultRdfParser(),
                    httpClientApache
            );

            closer.register(wikibaseRepository);

            UrisScheme wikibaseUris = WikibaseOptions.wikibaseUris(options);
            URI root = wikibaseRepository.getUris().builder().build();
            URI sparqlUri = UpdateOptions.sparqlUri(options);

            HttpClient httpClientJetty = HttpClientUtils.buildHttpClient(HttpClientUtils.getHttpProxyHost(), HttpClientUtils.getHttpProxyPort());
            closer.register(wrapHttpClient(httpClientJetty));
            Retryer<ContentResponse> retryer = HttpClientUtils.buildHttpClientRetryer();

            Duration rdfClientTimeout = Update.getRdfClientTimeout();
            RdfClient rdfClient = new RdfClient(httpClientJetty, sparqlUri, retryer, rdfClientTimeout);
            RdfRepository rdfRepository = new RdfRepository(wikibaseUris, rdfClient, MAX_FORM_CONTENT_SIZE);

            Instant startTime = ChangeSourceContext.getStartTime(UpdateOptions.startInstant(options), rdfRepository, options.init());
            Source<? extends Batch> changeSource = buildChangeSource(options, startTime, wikibaseRepository, rdfClient, root, metricRegistry);
            Munger munger = OptionsUtils.mungerFromOptions(options);
            ExecutorService updaterExecutorService = createUpdaterExecutorService(wbStackUpdaterThreadCount);
            Updater<? extends Batch> updater = createUpdater(
                    wikibaseRepository, wikibaseUris, rdfRepository, changeSource,
                    munger, updaterExecutorService, options.importAsync(),
                    options.pollDelay(), options.verify(), metricRegistry);
            closer.register(updater);

            return updater;
        } catch (Exception var19) {
            log.error("Error during initialization.", var19);
            throw var19;
        }
    }

    private static StreamDumper createStreamDumper(Path dumpDir) {
        return (StreamDumper) (dumpDir == null ? new NullStreamDumper() : new FileStreamDumper(dumpDir));
    }

    private static Updater<? extends Batch> createUpdater(
            WikibaseRepository wikibaseRepository,
            UrisScheme uris,
            RdfRepository rdfRepository,
            Source<? extends Batch> changeSource,
            Munger munger,
            ExecutorService executor,
            boolean importAsync,
            int pollDelay,
            boolean verify,
            MetricRegistry metricRegistry) {
        return new Updater(changeSource, wikibaseRepository, rdfRepository, munger, executor, importAsync, pollDelay, uris, verify, metricRegistry);
    }

    private static ExecutorService createUpdaterExecutorService(int threadCount) {
        return createUpdaterExecutorService(threadCount, 2147483647);
    }

    private static ExecutorService createUpdaterExecutorService(int threadCount, int queueSize) {
        ThreadFactoryBuilder threadFactory = (new ThreadFactoryBuilder()).setDaemon(true).setNameFormat("update %s");
        return new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue(queueSize), threadFactory.build());
    }

    private static Closeable wrapHttpClient(HttpClient httpClient) {
        return () -> {
            try {
                httpClient.stop();
            } catch (Exception e) {
                throw new RuntimeException("Could not close HttpClient", e);
            }
        };
    }

    private static MetricRegistry createMetricRegistry(Closer closer, String metricDomain) {
        MetricRegistry metrics = new MetricRegistry();
        JmxReporter reporter = (JmxReporter) closer.register(JmxReporter.forRegistry(metrics).inDomain(metricDomain).build());
        reporter.start();
        return metrics;
    }

    static {
        Security.setProperty("networkaddress.cache.negative.ttl", "5");
    }

    private static Properties loadBuildProperties() {
        Properties prop = new Properties();
        try (InputStream instream = Update.class.getClassLoader().getResourceAsStream("git.properties");) {
            prop.load(instream);
        } catch (IOException ioe) {
            log.warn("Failed to load properties file", ioe);
        }
        return prop;
    }
}
