Try to push this upstream somehow...

https://gerrit.wikimedia.org/r/#/c/wikidata/query/rdf/+/589408/

**The idea of speeding up the updater from version 1**

The idea of this is to avoid the JVM startup etc, and basically have an updater running all the time rather than shelling out which is what the first version glue did.

I also investigated nailgun, but its not really maintained anymore so probably not a good direction to go in.

This can be trialed at https://repl.it/languages/java
Just copy the code from the class into the default Main.java there and run the 2 lines below.
```
javac -classpath .:/run_dir/junit-4.12.jar:target/dependency/* -d . Main.java
java -classpath .:/run_dir/junit-4.12.jar:target/dependency/* org.wikidata.query.rdf.tool.WbStackUpdate --sparqlUrl sparql
```

This updater approach would require minimum changes to query service code and use the WHOLE of the current updater.
Pulls of new code would be easy, the only thing we would need to look out for are changes to the params that are passed into the updater that we manipulate.
But the runUpdater.sh could be altered to take a main class from an ENV var, and voila!

TODO check with wdqs team about if there are any wdqs internals I'll mess up by doing this & get their general thoughts.

## Running locally

1. In IntelliJ IDEA, create a run configuration for `org.wikidata.query.rdf.tool.WbStackUpdate`
2. Set VM Options to `-Xmx64m` for example
3. Set environment values as:

```
WBSTACK_API_ENDPOINT=http://localhost:3030/
WBSTACK_BATCH_SLEEP=0
WBSTACK_LOOP_LIMIT=1000000000
WBSTACK_WIKIBASE_SCHEME=http
```

4. Start docker with `docker-compose up`
5. As everything has initialized you should be able to run the new configuration.
6. Every time the fake api gets polled new items will get inserted into wikibase, and the updater will keep running indefinitely.
7. (Optional) https://visualvm.github.io/ for profiling   