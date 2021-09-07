'use strict';

const { assert, action, clientFactory } = require('../index');

describe('Client Factory', () => {

    it('Should share supertest agent between REST and Action API Clients', async () => {
        const actionAgent = await action.alice();
        const aliceUsername = actionAgent.username;
        const aliceUserId = actionAgent.userid;

        const restAgent = clientFactory.getRESTClient('', actionAgent);
        assert.strictEqual(restAgent.req, actionAgent.req);
        assert.propertyVal(restAgent, 'username', aliceUsername);
        assert.propertyVal(restAgent, 'userid', aliceUserId);

        const actionAgentFromRest = clientFactory.getActionClient(restAgent);
        assert.strictEqual(restAgent.req, actionAgentFromRest.req);
        assert.propertyVal(restAgent, 'username', aliceUsername);
        assert.propertyVal(restAgent, 'userid', aliceUserId);
    });

    it('Should return a test client representing an anonymous user when agent is not supplied to constructor',
        async () => {
            // TODO: This should actually test the cookie jar itself.
            const restAgent = clientFactory.getRESTClient();
            assert.propertyVal(restAgent, 'username', '<anon>');
            assert.propertyVal(restAgent, 'userid', 0);

            const actionAgent = clientFactory.getActionClient();
            assert.propertyVal(actionAgent, 'username', '<anon>');
            assert.propertyVal(actionAgent, 'userid', 0);
        }
    );

    it('Should return a REST test client for a specific endpoint',
        async () => {
            // TODO: This should actually test the cookie jar itself.
            // TODO: Don't use private member, use mocks ?
            const restAgent = clientFactory.getRESTClient('/some/path');
            assert.propertyVal(restAgent, 'pathPrefix', '/some/path');
        }
    );

    it('Should return a supertest agent with an empty cookie jar', async () => {
        const httpAgent = clientFactory.getHttpClient();
        // empty string indicates no specified app domain
        assert.equal(httpAgent.app, '');

    });

    it('Should return a supertest agent with a cookie jar set', async () => {
        const actionAgent = await action.alice();
        const httpAgentWithUser = clientFactory.getHttpClient(actionAgent);
        assert.strictEqual(httpAgentWithUser.jar, actionAgent.req.jar);
    });

});
