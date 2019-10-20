TODO updater

https://github.com/wikimedia/wikidata-query-rdf/blob/master/tools/src/main/java/org/wikidata/query/rdf/tool/options/UpdateOptions.java#L69-L74

    @Option(defaultToNull = true, description = "If specified must be <id> or list of <id>, comma or space separated.")
    List<String> ids();

    @Option(defaultToNull = true, description = "If specified must be <start>-<end>. Ids are iterated instead of recent "
            + "changes. Start and end are inclusive.")
    String idrange();
