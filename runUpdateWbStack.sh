#!/usr/bin/env bash
set -e
# This updater script has been created with the idea of serving https://www.wbstack.com/
# where multiple wikis will be updated using a single updater executor.
# If this works the idea may be refactored to allow for more flexibility for similar use cases.

# Call runUpdate with all of the magic stuff that is in there for logging and JVM etc
# But don't actually pass any real arguments into Java
# Also pass in our custom Main class that will handle the "magic"
# TODO also pass class path in...
./runUpdate.sh \
 -m org.wikidata.query.rdf.tool.WbStackUpdate \
 -h IGNOREDHOST \
 -n IGNOREDNAMESPACE \
 -- \
 --wikibaseHost IGNOREDWIKIBASEHOST \
 --wikibaseScheme IGNOREDSCHEME \
 --conceptUri IGNOREDCONCEPTURI \
 --entityNamespaces IGNOREDENTITYNS \
 --ids IGNOREDIDS

