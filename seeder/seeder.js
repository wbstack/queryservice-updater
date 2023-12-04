#!/usr/bin/env node

const createEntities = require('./create-entities');

const numEntities = parseInt(process.argv[2], 10)
if (!Number.isFinite(numEntities)) {
    throw new Error(`Expected numeric argument to be given, got ${process.argv[2]}`)
}

;(async function () {
    const result = await createEntities(numEntities)
    return `Sucessfully created ${result.length} entities`
})()
    .then((result) => {
        console.log(result)
    })
    .catch((err) => {
        console.error("Failed to run command.")
        console.error(err)
        process.exit(1)
    })
