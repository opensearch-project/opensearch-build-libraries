/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils.tests

import groovy.json.JsonSlurperClassic
import org.junit.Before
import org.junit.Test
import utils.SecurityAdvisoriesQuery
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class TestSecurityAdvisoriesQuery {
    def script
    def scriptArgs
    String response

    @Before
    void setUp() {
        response = '{"hits":{"hits":[{"_index":"scans-000335"}]}}'
        script = new Expando()
        script.sh = { Map args ->
            scriptArgs = args
            return response
        }
        script.println = { message -> }
        script.error = { String message -> throw new RuntimeException(message) }
    }

    @Test
    void testSearchTargetsExplicitIndexWithSigV4Auth() {
        def advisoriesQuery = new SecurityAdvisoriesQuery("advisoriesUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        def result = advisoriesQuery.search("advisories", "{\\\"query\\\":{}}")
        assertEquals(result, new JsonSlurperClassic().parseText(response))
        assertTrue(scriptArgs.script.contains('-XGET "advisoriesUrl/advisories/_search"'))
        assertTrue(scriptArgs.script.contains('--aws-sigv4 "aws:amz:us-east-1:es"'))
        assertTrue(scriptArgs.script.contains('--user "awsAccessKey:awsSecretKey"'))
        assertTrue(scriptArgs.script.contains('x-amz-security-token:awsSessionToken'))
    }

    @Test
    void testEmptyHitsIsAValidResponseNotAnError() {
        // No vulnerabilities found is a legitimate result, not a failure: it must parse and return.
        response = '{"hits":{"hits":[]}}'
        def advisoriesQuery = new SecurityAdvisoriesQuery("advisoriesUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        def result = advisoriesQuery.search("advisories", "{\\\"query\\\":{}}")
        assertEquals([], result.hits.hits)
    }

    @Test
    void testEmptyBodyThrows() {
        // A blank body (e.g. dropped connection) must throw rather than blow up in the JSON parser.
        response = ''
        def advisoriesQuery = new SecurityAdvisoriesQuery("advisoriesUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        try {
            advisoriesQuery.search("advisories", "{\\\"query\\\":{}}")
            fail("Expected RuntimeException on an empty response body")
        } catch (RuntimeException e) {
            assertTrue(e.message.contains('empty response'))
        }
    }

    @Test
    void testClusterErrorResponseThrows() {
        // An error envelope parses as valid JSON but has no hits; it must throw so the caller records
        // the criterion as unknown instead of mistaking it for zero results.
        response = '{"error":{"type":"search_phase_execution_exception","reason":"all shards failed"},"status":503}'
        def advisoriesQuery = new SecurityAdvisoriesQuery("advisoriesUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        try {
            advisoriesQuery.search("advisories", "{\\\"query\\\":{}}")
            fail("Expected RuntimeException on a cluster error response")
        } catch (RuntimeException e) {
            assertTrue(e.message.contains('query failed'))
        }
    }
}
