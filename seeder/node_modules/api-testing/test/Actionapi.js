'use strict';

const { assert, action } = require('../index');

describe('Action Api', () => {

    describe('Tag creation', () => {
        let alice;

        before(async () => {
            alice = await action.alice();
        });

        // If running this locally, the tag will already exist after the test has run once.
        it('Should create a new tag and return existing', async () => {
            const tagName = 'api-test';
            const tagDisplay = 'TestTagDisplay';
            const tag = await action.makeTag(tagName, `''${tagDisplay}''`);
            assert.deepEqual(tag, tagName);

            const tagList = await alice.list('tags', { tglimit: 50, tgprop: 'displayname' });
            assert.deepInclude(tagList, { name: tagName, displayname: `<i>${tagDisplay}</i>` });

            // If tag has already been created, should still return because it has been cached
            const tag2 = await action.makeTag(tagName);
            assert.deepEqual(tag2, tagName);
        });

    });
});
