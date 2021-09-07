var http = require('http');
var url = require('url');

var x = 0
http.createServer(function (req, res) {
    res.writeHead(200, {'Content-Type': 'text/json'});

    numEntities = 10;
    entities = [];

    for(var i=1; i <= numEntities; ++i){
        entities.push("Q" + (i+x))
    }

    x += numEntities;


    responseObject = {
        'entityIds': entities.join(','),
        'wiki': {
            'domain': 'default.web.mw.localhost:8080/',
            'wiki_queryservice_namespace': {
                'backend': 'localhost:9999',
                'namespace': 'wdq'
            }
        },

    };
    res.end(JSON.stringify([responseObject]));
}).listen(3030);