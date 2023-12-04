const http = require('http');
const assert = require('assert');
const createEntities = require('./create-entities');

let batchId = 1;

http.createServer(function (req, res) {
    (async () => {
        switch (req.url) {
        case '/markDone':
        case '/markNotDone':
            if (req.method !== 'POST') {
                const err = new Error('Method not allowed');
                err.status = 405;
                return Promise.reject(err);
            }
            return new Promise((resolve, reject) => {
                const body = [];
                req
                    .on('error', (err) => {
                        reject(err);
                    })
                    .on('data', (chunk) => {
                        body.push(chunk);
                    })
                    .on('end', () => {
                        try {
                            const jsonBody = JSON.parse(
                                Buffer.concat(body).toString('utf8')
                            );
                            assert(
                                Array.isArray(jsonBody.batches),
                                'Expected a `batches` property on the request body.'
                            );
                            assert(
                                jsonBody.batches.length,
                                'Expected `batches` to be a non-empty array.'
                            );
                        } catch (err) {
                            reject(err);
                            return;
                        }
                        resolve({ status: 200, body: '1' });
                    })
            });
        case '/getBatches':
            if (req.method !== 'GET') {
                const err = new Error('Method not allowed');
                err.status = 405;
                return Promise.reject(err);
            }
            const numEntities = 20;
            const entities = await createEntities(numEntities);

            responseObject = {
                'id': batchId++,
                'entityIds': entities.map(e => e.id).join(','),
                'wiki': {
                    'domain': process.env.API_WIKIBASE_DOMAIN,
                    'wiki_queryservice_namespace': {
                        'backend': process.env.API_WDQS_BACKEND,
                        'namespace': 'wdq'
                    }
                },

            };
            return {
                status: 200,
                headers: {'Content-Type': 'text/json'},
                body: JSON.stringify([responseObject])
            };
        default:
            const err = new Error('Not found');
            err.status = 404;
            return Promise.reject(err);
        }
    })()
        .catch((err) => {
            console.error('[FAILURE] Failed handling request: %s', err.message);
            return { status: err.status || 500, body: err.message };
        })
        .then((result) => {
            res.writeHead(result.status, result.headers);
            res.end(result.body)
        })
}).listen(3030);
