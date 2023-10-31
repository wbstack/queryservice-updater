var http = require('http');
const wbEdit = require( 'wikibase-edit' )( require( './wikibase-edit.config' ) );

let batchId = 1;

http.createServer(function (req, res) {
    (async () => {
        switch (req.url) {
        case '/markDone':
        case '/markFailed':
            if (req.method !== 'POST') {
                return { status: 405, body: 'Method not allowed' };
            }
            return { status: 200, body: '1' };
        case '/getBatches':
            if (req.method !== 'GET') {
                return { status: 405, body: 'Method not allowed' };
            }
            const numEntities = 20;
            const entities = [];

            for (let i = 1; i <= numEntities; ++i) {
                const { entity } = await wbEdit.entity.create({
                    type: 'item',
                    labels: {
                        'en': new Date().toISOString()
                    },
                    descriptions: {
                        'en': new Date().toDateString() + new Date().toISOString()
                    }
                });

                console.log('created item id', entity.id)
                entities.push(entity.id)
            }

            console.log(new Date().toISOString());

            responseObject = {
                'id': batchId++,
                'entityIds': entities.join(','),
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
            return { status: 404, body: 'Not found' };
        }
    })()
        .catch((err) => {
            console.error('Failed handling request: %s', err.message);
            return { status: 500, body: err.message };
        })
        .then((result) => {
            res.writeHead(result.status, result.headers);
            res.end(result.body)
        })
}).listen(3030);
