FROM composer as composer

COPY . /tmp/src

RUN \
cd /tmp/src && \
composer install --no-dev --no-progress --optimize-autoloader


FROM gcr.io/wbstack/queryservice:0.3.6-0.1

RUN apk add php php-json

COPY --from=composer /tmp/src /wdqs-updater
ENTRYPOINT php /wdqs-updater/run.php
