var http = require('http');
const wbEdit = require( 'wikibase-edit' )( require( './wikibase-edit.config' ) );

var x = 0
http.createServer(async function (req, res) {
    res.writeHead(200, {'Content-Type': 'text/json'});

    numEntities = 20;
    entities = [];

    for(var i=1; i <= numEntities; ++i){
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

    x += numEntities;
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
    res.end(JSON.stringify([responseObject]));
}).listen(3030);
