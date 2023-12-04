#!/usr/bin/env node

const wbEdit = require('wikibase-edit')(require('./wikibase-edit.config'));

const numEntities = parseInt(process.argv[2], 10)
if (!Number.isFinite(numEntities)) {
    throw new Error(`Expected numeric argument to be given, got ${process.argv[2]}`)
}

;(async function () {
    for (let i = 1; i <= numEntities; i++) {
        await wbEdit.entity.create({
            type: 'item',
            labels: {
                'en': new Date().toISOString()
            },
            descriptions: {
                'en': new Date().toDateString() + new Date().toISOString()
            }
        })
    }

    return `Sucessfully created ${numEntities} entities`
})()
    .then((result) => {
        console.log(result)
    })
    .catch((err) => {
        console.error("Failed to run command.")
        console.error(err)
        process.exit(1)
    })
