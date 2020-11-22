FROM maven:3.6.3-jdk-8 as jarjar

COPY ./java /tmp
WORKDIR /tmp
RUN mvn clean compile assembly:single


FROM openjdk:8-jdk-alpine

LABEL org.opencontainers.image.source="https://github.com/wbstack/queryservice-updater"

RUN addgroup -S updater && adduser -S updater -G updater \
&& apk add bash

# Don't set a memory limit otherwise bad things happen (OOMs)
# TODO this was shamelessly copied from the current wmde/wikibase-docker wdqs image....
ENV MEMORY=""\
    HEAP_SIZE="1g"\
    HOST="0.0.0.0"\
    WDQS_ENTITY_NAMESPACES="120,122"\
    WIKIBASE_SCHEME="http"\
    WIKIBASE_MAX_DAYS_BACK="90"

WORKDIR /wdqsup

COPY --chown=updater:updater --from=jarjar /tmp/target/wbstack-queryservice-0.3.6-0.1-jar-with-dependencies.jar /wdqsup/wbstackqs.jar
COPY --chown=updater:updater ./scripts/ /wdqsup/

# TODO is this actually needed?
RUN chmod +x /wdqsup/runUpdate.sh \
&& chmod +x /wdqsup/runUpdateWbStack.sh

USER updater:updater

ENTRYPOINT /wdqsup/runUpdateWbStack.sh
