# syntax=docker/dockerfile:1
FROM maven:3.6.3-jdk-8 AS jarjar

WORKDIR /tmp
COPY pom.xml ./
RUN --mount=type=cache,id=queryservice-updater-m2,target=/root/.m2 mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY . ./
RUN --mount=type=cache,id=queryservice-updater-m2,target=/root/.m2 mvn --batch-mode --no-transfer-progress compile assembly:single


FROM eclipse-temurin:8-jdk-alpine

LABEL org.opencontainers.image.source="https://github.com/wbstack/queryservice-updater"

RUN addgroup -S updater && adduser -S updater -G updater \
&& apk add --no-cache bash

# Don't set a memory limit otherwise bad things happen (OOMs)
# TODO this was shamelessly copied from the current wmde/wikibase-docker wdqs image...
ENV MEMORY=""\
    HEAP_SIZE="1g"\
    HOST="0.0.0.0"\
    WDQS_ENTITY_NAMESPACES="120,122"\
    WIKIBASE_SCHEME="http"\
    WIKIBASE_MAX_DAYS_BACK="90"

WORKDIR /wdqsup

COPY --chown=updater:updater --from=jarjar /tmp/target/wbstack-queryservice-0.3.6-0.1-jar-with-dependencies.jar /wdqsup/wbstackqs.jar
COPY --chown=updater:updater ./runUpdate.sh ./runUpdateWbStack.sh /wdqsup/

USER updater:updater

ENTRYPOINT /wdqsup/runUpdateWbStack.sh
