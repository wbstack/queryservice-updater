package org.wikidata.query.rdf.tool;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import com.github.rholder.retry.Retryer;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.query.rdf.common.uri.UrisScheme;
import org.wikidata.query.rdf.tool.change.ChangeSourceContext;
import org.wikidata.query.rdf.tool.options.OptionsUtils;
import org.wikidata.query.rdf.tool.options.UpdateOptions;
import org.wikidata.query.rdf.tool.rdf.*;
import org.wikidata.query.rdf.tool.rdf.client.RdfClient;
import org.wikidata.query.rdf.tool.utils.FileStreamDumper;
import org.wikidata.query.rdf.tool.utils.NullStreamDumper;
import org.wikidata.query.rdf.tool.utils.StreamDumper;
import org.wikidata.query.rdf.tool.wikibase.WikibaseRepository;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.Security;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import org.wikidata.query.rdf.tool.change.Change.Batch;
import org.wikidata.query.rdf.tool.change.Change.Source;
import org.wikidata.query.rdf.tool.options.OptionsUtils.WikibaseOptions;
import org.wikidata.query.rdf.tool.rdf.Munger;
import org.wikidata.query.rdf.tool.rdf.RdfRepository;


class WbStackUpdate {

    private static final String USER_AGENT = "WBStack - Query Service - Updater";
    private static String wbStackApiEndpoint;
    private static long wbStackSleepBetweenApiCalls;
    private static String wbStackWikibaseScheme;
    private static Integer wbStackUpdaterThreadCount;

    private static Gson gson;
    private static MetricRegistry metricRegistry;
    private static CloseableHttpClient client;
    private static HttpClientConnectionManager manager;
    private static Properties buildProps;

    private static final Logger log = LoggerFactory.getLogger(org.wikidata.query.rdf.tool.WbStackUpdate.class);
    private static final long MAX_FORM_CONTENT_SIZE = Long.getLong("RDFRepositoryMaxPostSize", 200000000L);

    private static Closer metricsCloser;

    private static void setValuesFromEnvOrDie() {
        if( System.getenv("WBSTACK_API_ENDPOINT") == null || System.getenv("WBSTACK_BATCH_SLEEP") == null || System.getenv("WBSTACK_LOOP_LIMIT") == null ) {
            System.err.println("WBSTACK_API_ENDPOINT and WBSTACK_BATCH_SLEEP environment variables must be set.");
            System.exit(1);
        }

        wbStackApiEndpoint = System.getenv("WBSTACK_API_ENDPOINT");
        wbStackSleepBetweenApiCalls = Integer.parseInt(System.getenv("WBSTACK_BATCH_SLEEP"));
        wbStackUpdaterThreadCount = Integer.parseInt(System.getenv().getOrDefault("WBSTACK_THREAD_COUNT", "10" ));
        wbStackWikibaseScheme = System.getenv().getOrDefault("WBSTACK_WIKIBASE_SCHEME", "https" );
    }

    public static void main(String[] args) throws InterruptedException {
        setValuesFromEnvOrDie();
        int count = 0;
        int countLimit = Integer.parseInt( System.getenv("WBSTACK_LOOP_LIMIT") );

        long loopLastStarted;
        Runtime runtime = Runtime.getRuntime();
        gson = new GsonBuilder().setPrettyPrinting().create();
        buildProps = loadBuildProperties();
        metricsCloser = Closer.create();
        metricRegistry = createMetricRegistry(metricsCloser, "wdqs-updater");

        // TODO actually set to run for 1 hour or something?
        while (count < countLimit) {
            count++;
            loopLastStarted = System.currentTimeMillis();
            mainLoop();
            long memory = runtime.totalMemory() - runtime.freeMemory();
            System.out.println(
                            "Loop " + count + "/" + countLimit + ". " +
                            "Total mem:" + (runtime.totalMemory() / (1024L * 1024L)) + ". " +
                            "Free mem:" + (runtime.freeMemory() / (1024L * 1024L)) + ". " +
                            "Used mem:" + (memory / (1024L * 1024L)) + ". " +
                            "Max mem:" + (runtime.maxMemory() / (1024L * 1024L)) + ". "
            );
            sleepForRemainingTimeBetweenLoops( loopLastStarted );
        }

        System.out.println("Finished " + count + " runs. Exiting...");

        try {
            metricsCloser.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void mainLoop() {
        // Get the list of batches from the API and process them
        try {
            final JsonArray batches = doGetRequest( wbStackApiEndpoint );
            for (JsonElement batchElement : batches) {
                runBatch( batchElement );
            }
        } catch (IOException e) {
            // On IOException, output and go onto the next loop (with a sleep)
            System.err.println(e.getMessage());
        }
    }

    private static JsonArray doGetRequest( String requestUrl ) throws IOException {
        URL obj = new URL(requestUrl);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);
        int responseCode = con.getResponseCode();
        if( responseCode != 200 ) {
            throw new IOException("Got non 200 response code: " + responseCode);
        }
        JsonElement batches = null;

        try (JsonReader reader = new JsonReader(new InputStreamReader(con.getInputStream()))) {
            batches = gson.fromJson(reader, JsonElement.class);
            if (batches == null) {
                System.err.println("Failed to get JsonArray from jsonString (returning empty)");
                return new JsonArray();
            }
            return batches.getAsJsonArray();
        } catch (JsonSyntaxException e) {
            System.err.println("Failed to get JSON from str");
            return new JsonArray();
        }
    }


    private static void runBatch( JsonElement batchElement ) {
        // Get the values for the batch from the JSON
        JsonObject batch = batchElement.getAsJsonObject();
        String entityIDs = batch.get("entityIds").getAsString();
        JsonObject wiki = batch.get("wiki").getAsJsonObject();
        String domain = wiki.get("domain").getAsString();
        JsonObject wikiQsNamespace = wiki.get("wiki_queryservice_namespace").getAsJsonObject();
        String qsBackend = wikiQsNamespace.get("backend").getAsString();
        String qsNamespace = wikiQsNamespace.get("namespace").getAsString();

        // Run the main Update class with our altered args
        runUpdaterWithArgs(new String[] {
                "--sparqlUrl", "http://" + qsBackend + "/bigdata/namespace/" + qsNamespace + "/sparql",
                "--wikibaseHost", domain,
                "--wikibaseScheme", wbStackWikibaseScheme,
                "--conceptUri", "http://" + domain,
                "--entityNamespaces", "120,122,146",
                "--ids", entityIDs
        });
        // TODO on success maybe report back?
    }

    private static void sleepForRemainingTimeBetweenLoops(long timeApiRequestDone ) throws InterruptedException {
        long secondsRunning = (System.currentTimeMillis() - timeApiRequestDone)/1000;
        if(secondsRunning < wbStackSleepBetweenApiCalls ) {
            TimeUnit.SECONDS.sleep(wbStackSleepBetweenApiCalls-secondsRunning);
        }
    }

    private static void runUpdaterWithArgs(String[] args) {
        System.out.print("Running updater with args: ");
        for(String s : args){
            System.out.print(s + " ");
        }
        System.out.println("");

        try {
            Closer closer = Closer.create();

            try {
                log.info("Starting Updater {} ({})", buildProps.getProperty("git.build.version", "UNKNOWN"), buildProps.getProperty("git.commit.id", "UNKNOWN"));
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
                client.close();
                manager.shutdown();
            }

        } catch (Exception e) {
            System.err.println("Failed batch!");
            e.printStackTrace();
        }
    }

    private static Updater<? extends Batch> initialize(String[] args, Closer closer) throws URISyntaxException {
        try {
            UpdateOptions options = (UpdateOptions)OptionsUtils.handleOptions(UpdateOptions.class, args);
            StreamDumper wikibaseStreamDumper = createStreamDumper(UpdateOptions.dumpDirPath(options));

            // Don't use the ConnectionManager from HttpClientUtils as it constantly spawns Eviction threads that won't stop
            manager  = new PoolingHttpClientConnectionManager(-1, TimeUnit.SECONDS);
            client = HttpClientUtils.createHttpClient( manager, null, null, 1000);
            WikibaseRepository wikibaseRepository = new WikibaseRepository(
                    UpdateOptions.uris(options),
                    options.constraints(),
                    metricRegistry,
                    wikibaseStreamDumper,
                    UpdateOptions.revisionDuration(options),
                    RDFParserSuppliers.defaultRdfParser(),
                    client
            );

            closer.register(wikibaseRepository);

            UrisScheme wikibaseUris = WikibaseOptions.wikibaseUris(options);
            URI root = wikibaseRepository.getUris().builder().build();
            URI sparqlUri = UpdateOptions.sparqlUri(options);
            HttpClient httpClient = HttpClientUtils.buildHttpClient(HttpClientUtils.getHttpProxyHost(), HttpClientUtils.getHttpProxyPort());
            closer.register(wrapHttpClient(httpClient));
            Retryer<ContentResponse> retryer = HttpClientUtils.buildHttpClientRetryer();

            Duration rdfClientTimeout = org.wikidata.query.rdf.tool.Update.getRdfClientTimeout();

            RdfClient rdfClient = new RdfClient(httpClient, sparqlUri, retryer, rdfClientTimeout);
            RdfRepository rdfRepository = new RdfRepository(wikibaseUris, rdfClient, MAX_FORM_CONTENT_SIZE);
            Instant startTime = ChangeSourceContext.getStartTime(UpdateOptions.startInstant(options), rdfRepository, options.init());
            Source<? extends Batch> changeSource = ChangeSourceContext.buildChangeSource(options, startTime, wikibaseRepository, rdfClient, root, metricRegistry);
            Munger munger = OptionsUtils.mungerFromOptions(options);
            ExecutorService updaterExecutorService = createUpdaterExecutorService(wbStackUpdaterThreadCount);
            Updater<? extends Batch> updater = createUpdater(wikibaseRepository, wikibaseUris, rdfRepository, changeSource, munger, updaterExecutorService, options.importAsync(), options.pollDelay(), options.verify(), metricRegistry);
            closer.register(updater);
            return updater;
        } catch (Exception var19) {
            log.error("Error during initialization.", var19);
            throw var19;
        }
    }

    private static StreamDumper createStreamDumper(Path dumpDir) {
        return (StreamDumper)(dumpDir == null ? new NullStreamDumper() : new FileStreamDumper(dumpDir));
    }

    private static Updater<? extends Batch> createUpdater(WikibaseRepository wikibaseRepository, UrisScheme uris, RdfRepository rdfRepository, Source<? extends Batch> changeSource, Munger munger, ExecutorService executor, boolean importAsync, int pollDelay, boolean verify, MetricRegistry metricRegistry) {
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
            } catch (Exception var2) {
                throw new RuntimeException("Could not close HttpClient", var2);
            }
        };
    }

    private static MetricRegistry createMetricRegistry(Closer closer, String metricDomain) {
        MetricRegistry metrics = new MetricRegistry();
        JmxReporter reporter = (JmxReporter)closer.register(JmxReporter.forRegistry(metrics).inDomain(metricDomain).build());
        reporter.start();
        return metrics;
    }

    static {
        Security.setProperty("networkaddress.cache.negative.ttl", "5");
    }

    private static Properties loadBuildProperties() {
        Properties prop = new Properties();

        try {
            InputStream instream = org.wikidata.query.rdf.tool.Update.class.getClassLoader().getResourceAsStream("git.properties");
            Throwable var2 = null;

            try {
                prop.load(instream);
            } catch (Throwable var12) {
                var2 = var12;
                throw var12;
            } finally {
                if (instream != null) {
                    if (var2 != null) {
                        try {
                            instream.close();
                        } catch (Throwable var11) {
                            var2.addSuppressed(var11);
                        }
                    } else {
                        instream.close();
                    }
                }

            }
        } catch (IOException var14) {
            log.warn("Failed to load properties file");
        }

        return prop;
    }
}
