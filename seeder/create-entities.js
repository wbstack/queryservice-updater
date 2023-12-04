const wbEdit = require('wikibase-edit')(require('./wikibase-edit.config'));

module.exports = async function (numEntities) {
    const result = []
    for (let i = 1; i <= numEntities; i++) {
        const { entity } = await wbEdit.entity.create({
            type: 'item',
            labels: {
                'en': new Date().toISOString()
            },
            descriptions: {
                'en': new Date().toDateString() + new Date().toISOString()
            }
        })
        result.push(entity)
    }
    return result
}
