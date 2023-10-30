var http = require('http');
const wbEdit = require( 'wikibase-edit' )( require( './wikibase-edit.config' ) );

http.createServer(function (req, res) {
    (async () => {
        switch (req.url) {
        case '/getBatches':
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
                'entityIds': entities.join(','),
                'wiki': {
                    'domain': process.env.API_WIKIBASE_DOMAIN,
                    'wiki_queryservice_namespace': {
                        'backend': process.env.API_WDQS_BACKEND,
                        'namespace': 'wdq'
                    }
                },

            };
            res.writeHead(200, {'Content-Type': 'text/json'});
            res.end(JSON.stringify([responseObject]));
        default:
            res.writeHead(404);
            res.end('Not found');
        }
    })()
        .catch((err) => {
            console.error('Failed handling request: %s', err.message);
            res.writeHead(500);
            res.end(err.message);
        })
}).listen(3030);
