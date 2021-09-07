FROM node:latest

WORKDIR /usr/src/app/
COPY . .

RUN npm ci
RUN chmod +x /usr/src/app/entrypoint.sh
ENTRYPOINT /usr/src/app/entrypoint.sh