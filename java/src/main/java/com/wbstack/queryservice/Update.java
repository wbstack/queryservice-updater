package com.wbstack.queryservice;

import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

class Update {

    private static String wbStackApiEndpoint;
    private static long wbStackBatchSleep;

    private static void setValuesFromEnvOrDie() {
        if( System.getenv("WBSTACK_API_ENDPOINT") == null || System.getenv("WBSTACK_BATCH_SLEEP") == null ) {
            System.err.println("WBSTACK_API_ENDPOINT and WBSTACK_BATCH_SLEEP environment variables must be set.");
            System.exit(1);
        }

        wbStackApiEndpoint = System.getenv("WBSTACK_API_ENDPOINT");
        wbStackBatchSleep = Integer.parseInt(System.getenv("WBSTACK_BATCH_SLEEP"));
    }

    public static void main(String[] args) throws InterruptedException {
        setValuesFromEnvOrDie();

        // TODO actually set to run for 1 hour or something?
        while (true) {

            // Get the list of batches from the API
            String apiResultString = null;

            URL obj = null;
            try {
                obj = new URL(wbStackApiEndpoint);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", "WBStack Query Service Updater");
                int responseCode = con.getResponseCode();
                if( responseCode != 200 ) {
                    System.err.println("Got non 200 response code: " + responseCode);
                    TimeUnit.SECONDS.sleep(wbStackBatchSleep);
                    continue;
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                apiResultString = response.toString();
            } catch (IOException e) {
                e.printStackTrace();
                TimeUnit.SECONDS.sleep(wbStackBatchSleep);
                continue;
            }

            // Decode the API response into some Java objects
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonElement batches = null;
            Reader reader = new StringReader(apiResultString);
            // Convert JSON to JsonElement, and later to String
            batches = gson.fromJson(reader, JsonElement.class);

            if (batches == null) {
                //System.out.println("No batches (null), so sleeping");
                TimeUnit.SECONDS.sleep(wbStackBatchSleep);
                continue;
            }

            JsonArray batchesArray = batches.getAsJsonArray();
            if (batchesArray.size() == 0) {
                //System.out.println("No batches (0), so sleeping");
                TimeUnit.SECONDS.sleep(wbStackBatchSleep);
                continue;
            }

            // Iterate through the batches
            for (JsonElement jsonElement : batchesArray) {
                // Get the values for the batch from the JSON
                JsonObject batch = jsonElement.getAsJsonObject();
                String entityIDs = batch.get("entityIds").getAsString();
                JsonObject wiki = batch.get("wiki").getAsJsonObject();
                String domain = wiki.get("domain").getAsString();
                JsonObject wikiQsNamespace = wiki.get("wiki_queryservice_namespace").getAsJsonObject();
                String qsBackend = wikiQsNamespace.get("backend").getAsString();
                String qsNamespace = wikiQsNamespace.get("namespace").getAsString();

                // Replace them in our args
                args = replaceValueForArg(args, "--sparqlUrl", "http://" + qsBackend + "/bigdata/namespace/" + qsNamespace + "/sparql");
                args = replaceValueForArg(args, "--wikibaseHost", domain);
                args = replaceValueForArg(args, "--wikibaseScheme", "https"); // TODO don't hard code this
                args = replaceValueForArg(args, "--conceptUri", "http://" + domain); // TODO don't hard code the scheme
                args = replaceValueForArg(args, "--entityNamespaces", "120,122,146"); // TODO don't hard code these
                args = replaceValueForArg(args, "--ids", entityIDs);

                // Run the main Update class with our altered args
                runUpdaterWithArgs(args);

                // Sleep between batches
                // TODO be clever here and if the batches were running for longer than the sleep time, dont sleep..
                try {
                    TimeUnit.SECONDS.sleep(wbStackBatchSleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }

    }

    private static String[] replaceValueForArg( String[] args, String argToReplace, String newValue ) {
        for( int i = 0; i < args.length - 1; i++)
        {
            if(args[i].equals(argToReplace)){
                args[i+1] = newValue;
                return args;
            }
        }

        // Die if we didnt manage to replace one, as something is wrong...
        System.err.println("Failed to replace argument: " + argToReplace);
        System.exit(1);
        throw new RuntimeException("Should have already exited");
    }

    private static void runUpdaterWithArgs(String[] args) {
        System.out.print("Running updater with args: ");
        for(String s : args){
            System.out.print(s + " ");
        }
        System.out.println("");

        try {
            org.wikidata.query.rdf.tool.Update.main( args );
        } catch (Exception e) {
            System.err.println("Failed batch!");
            e.printStackTrace();
        }
    }
}
