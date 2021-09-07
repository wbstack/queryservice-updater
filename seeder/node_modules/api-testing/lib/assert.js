'use strict';

const { use } = require('chai');
const utils = require('./utils');

module.exports = use(function (_chai, _utils) {
    const assert = _chai.assert;

    /**
     * Compares two titles, applying some normalization
     *
     * @param {string} act
     * @param {string} exp
     * @param {string} msg
     */
    assert.sameTitle = (act, exp, msg) => {
        new _chai.Assertion(utils.dbkey(act), msg, assert.deepEqual, true).to.eql(utils.dbkey(exp));
    };

    /**
     * Gets header assertion
     *
     * @param {Object} res Supertest Response object
     * @param {string} header Name of header to check
     * @param {string|RegExp} exp Expected header
     * @param {?string} msg
     */
    const getHeaderAssertion = (res, header, exp, msg) => {
        if (/^\/.+\/$/.test(exp)) {
            new _chai.Assertion(res.header[header.toLowerCase()], msg, true).to.match(exp);
        } else {
            new _chai.Assertion(res.header[header.toLowerCase()], msg, true).to.equal(exp);
        }
    };

    /**
     * Asserts whether a response's header was as expected
     *
     * @param {Object} res Supertest Response object
     * @param {string} header Name of header to check
     * @param {string|RegExp} exp Expected header
     * @param {?string} msg
     */
    assert.header = (res, header, exp, msg) => {
        getHeaderAssertion(res, header, exp, msg);
    };

    /**
     * Asserts whether a response's content type was as expected
     *
     * @param {Object} res Supertest Response object
     * @param {string|RegExp} exp Expected content type
     * @param {?string} msg
     */
    assert.contentType = (res, exp, msg) => {
        getHeaderAssertion(res, 'content-type', exp, msg);
    };
});
