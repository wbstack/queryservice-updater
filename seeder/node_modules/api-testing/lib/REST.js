'use strict';

const supertest = require('supertest');
const config = require('./config')();

class RESTClient {
    /**
     * Constructs a new agent for making HTTP requests to the MediaWiki
     * REST API. The agent acts like a browser session and has its own
     * cookie jar.
     * Pass in an optional supertest agent with user session information (cookie jar)
     * for the client to behave as a logged in user.
     *
     * @param {string} endpoint REST endpoint path
     * @param {Object} agent supertest agent
     */
    constructor(endpoint = 'rest.php/v1', agent = null) {
        this.pathPrefix = endpoint;
        if (agent) {
            this.req = agent.req;
            this.username = agent.username;
            this.userid = agent.userid;
        } else {
            this.req = supertest.agent(config.base_uri);
            this.username = '<anon>';
            this.userid = 0;
        }
    }

    /**
     * Constructs an HTTP request to the REST API and returns the
     * corresponding supertest Test object, which behaves like a
     * superagent Request. It can be used like a Promise that resolves
     * to a Response.
     *
     * Call end(), then(), or use await to send the request.
     *
     * @param {string} endpoint
     * @param {string} method
     * @param {Object|string} params
     * @param {Object} headers
     * @return {Promise<*>}
     */
    async request(endpoint, method, params = {}, headers = {}) {
        let req;
        endpoint = this.pathPrefix + endpoint;
        switch (method.toUpperCase()) {
            case 'GET':
                req = this.req.get(endpoint)
                    .query(params)
                    .set(headers);
                break;
            case 'POST':
                req = this.req.post(endpoint)
                    .send(params)
                    .set(headers);
                break;
            case 'PUT':
                req = this.req.put(endpoint)
                    .send(params)
                    .set(headers);
                break;
            case 'DELETE':
                req = this.req.del(endpoint)
                    .query(params)
                    .set(headers);
                break;
            default:
                throw new Error(`The following method is unsupported: ${method}`);
        }

        return req;
    }

    _objectKeysToLowerCase(headers) {
        return Object.keys(headers).reduce((updatedHeaders, key) => {
            updatedHeaders[key.toLowerCase()] = headers[key];
            return updatedHeaders;
        }, {});
    }

    /**
     * Constructs a GET request and returns the
     * corresponding supertest Test object
     *
     * @param {string} endpoint
     * @param {Object|null}params
     * @param {Object} headers
     * @return {Promise<*>}
     */
    async get(endpoint, params = {}, headers = {}) {
        return this.request(endpoint, 'GET', params, headers);
    }

    /**
     * Constructs a POST request and returns the
     * corresponding supertest Test object
     *
     * @param {string} endpoint
     * @param {Object|string} params
     * @param {Object} headers
     * @return {Promise<*>}
     */
    async post(endpoint, params = {}, headers = {}) {
        const updatedHeaders = this._objectKeysToLowerCase(headers);
        return this.request(endpoint, 'POST', params, Object.assign({ 'content-type': 'application/json' }, updatedHeaders));
    }

    /**
     * Constructs a PUT request and returns the
     * corresponding supertest Test object
     *
     * @param {string} endpoint
     * @param {Object|string} params
     * @param {Object} headers
     * @return {Promise<*>}
     */
    async put(endpoint, params = {}, headers = {}) {
        const updatedHeaders = this._objectKeysToLowerCase(headers);
        return this.request(endpoint, 'PUT', params, Object.assign({ 'content-type': 'application/json' }, updatedHeaders));
    }

    /**
     * Constructs a DELETE request and returns the
     * corresponding supertest Test object
     *
     * @param {string} endpoint
     * @param {Object} params
     * @param {Object} headers
     * @return {Promise<*>}
     */
    async del(endpoint, params = {}, headers = {}) {
        return this.request(endpoint, 'DELETE', params, headers);
    }
}

module.exports = RESTClient;
