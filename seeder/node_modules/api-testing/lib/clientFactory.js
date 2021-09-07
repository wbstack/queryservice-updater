'use strict';

const Client = require('./actionapi');
const REST = require('./REST');
const supertest = require('supertest');

/**
 * Factory for retrieving a supertest agent to call the action API or
 * the REST Api. Using the same agent allows us to emulate the same user hitting
 * both APIs as a single, logged in user.
 */
const clientFactory = {

    /**
     * Constructs a test REST Api client (aka a supertest agent) using the supplied
     * action api agent.
     *
     * @param {string} endpoint REST api endpoint to hit
     * @param {Object} actionClient supertest agent for making calls to Action Api
     * @return {Object} supertest agent for making calls to REST Api
     */
    getRESTClient: (endpoint, actionClient) => {
        return new REST(endpoint, actionClient);
    },

    /**
     * Constructs a test REST Api client (aka a supertest agent) using the supplied
     * rest api agent
     *
     * @param {Object} restClient supertest agent for making calls to Action Api
     * @return {Object} supertest agent for making calls to REST Api
     */
    getActionClient: (restClient) => {
        return new Client(restClient);
    },

    /**
     * Returns a supertest agent without a pre-defined domain to be used for making
     * http requests with an absolute URL.
     *
     * @param {Object} client rest or action client
     * @return {Object} supertest agent
     */
    getHttpClient: (client) => {
        const agent = supertest.agent('');
        if (client) {
            agent.jar = client.req.jar;
        }
        return agent;
    }
};

module.exports = clientFactory;
