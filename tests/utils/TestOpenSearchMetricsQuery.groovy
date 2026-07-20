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
import utils.OpenSearchMetricsQuery
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse
import static org.junit.Assert.fail

class TestOpenSearchMetricsQuery {
    def script
    def scriptArgs
    String response
    // Captures the JSON body written to the temp file by sendJson()
    String writtenBody
    // Captures the curl sh script specifically (sendJson also issues an 'rm' cleanup call)
    String curlScript

    @Before
    void setUp() {
        script = new Expando()

        script.sh = { Map args ->
            scriptArgs = args
            // Mock implementation to verify the file is written correctly
            response = """
                    {
                      "took": 4,
                      "timed_out": false,
                      "_shards": {
                        "total": 5,
                        "successful": 5,
                        "skipped": 0,
                        "failed": 0
                      },
                     }"""
            if (args.containsKey("script")) {
                return response
            }
        }

        // Captures the request body that sendJson writes before issuing the curl.
        script.writeFile = { Map args -> writtenBody = args.text }

        script.println = { message ->
            // Mock implementation for println
            println(message)
        }
    }

    @Test
    void testOpenSearchMetricsQuery() {
        def query = "{\\\"size\\\":1,\\\"_source\\\":[\\\"coverage\\\",\\\"branch\\\",\\\"state\\\",\\\"url\\\"],\\\"query\\\":{\\\"bool\\\":{\\\"filter\\\":[{\\\"match_phrase\\\":{\\\"repository.keyword\\\":\\\"OpenSearch\\\"}},{\\\"match_phrase\\\":{\\\"version\\\":\\\"2.19.0\\\"}}]}},\\\"sort\\\":[{\\\"current_date\\\":{\\\"order\\\":\\\"desc\\\"}}]}"
        def expectedScript = """
            set -e
            set +x
            curl -s -XGET "metricsUrl/sampleIndex/_search" --aws-sigv4 "aws:amz:us-east-1:es" --user "awsAccessKey:awsSecretKey" -H "x-amz-security-token:awsSessionToken" -H 'Content-Type: application/json' -d "${query}" | jq '.'
            """
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", "sampleIndex", this.script)
        def result  = metricsQuery.fetchMetrics(query)
        assertEquals(result, new JsonSlurperClassic().parseText(response))
        assertEquals(expectedScript.trim(), scriptArgs.script.trim())
    }

    @Test
    void testIndexExistsReturnsTrueOn200() {
        script.sh = { Map args ->
            scriptArgs = args
            return '200'
        }
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        assertTrue(metricsQuery.indexExists("sampleIndex"))
        assertTrue(scriptArgs.script.contains('-I "metricsUrl/sampleIndex"'))
        assertTrue(scriptArgs.script.contains('--aws-sigv4 "aws:amz:us-east-1:es"'))
    }

    @Test
    void testIndexExistsReturnsFalseOnNon200() {
        script.sh = { Map args ->
            scriptArgs = args
            return '404'
        }
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        assertFalse(metricsQuery.indexExists("missingIndex"))
    }

    @Test
    void testCreateIndexSendsMappingAsJsonViaFile() {
        // The curl call returns the status; the cleanup 'rm' call returns anything.
        script.sh = { Map args ->
            if (args.script.contains('curl')) {
                curlScript = args.script
                return '200'
            }
            return 0
        }
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        metricsQuery.createIndex("sampleIndex", [mappings: [properties: [version: [type: 'keyword']]]])
        // PUT to the index, body passed via file (-d @...), and the mapping serialized into that file.
        assertTrue(curlScript.contains('-XPUT "metricsUrl/sampleIndex"'))
        assertTrue(curlScript.contains('-d @'))
        assertEquals('{"mappings":{"properties":{"version":{"type":"keyword"}}}}', writtenBody)
    }

    @Test
    void testCreateIndexThrowsOnNon200() {
        script.sh = { Map args ->
            scriptArgs = args
            return args.script.contains('curl') ? '400' : 0
        }
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        try {
            metricsQuery.createIndex("sampleIndex", [mappings: [properties: [:]]])
            fail("Expected RuntimeException when cluster returns non-200")
        } catch (RuntimeException e) {
            assertTrue(e.message.contains("Failed to create index sampleIndex"))
            assertTrue(e.message.contains("400"))
        }
    }

    @Test
    void testIndexDocumentSendsBodyAsJsonViaFile() {
        script.sh = { Map args ->
            if (args.script.contains('curl')) {
                curlScript = args.script
                return '201'
            }
            return 0
        }
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        metricsQuery.indexDocument("sampleIndex", [doc_type: 'criterion', version: '3.8.0'])
        assertTrue(curlScript.contains('-XPOST "metricsUrl/sampleIndex/_doc"'))
        assertTrue(curlScript.contains('-d @'))
        assertEquals('{"doc_type":"criterion","version":"3.8.0"}', writtenBody)
    }

    @Test
    void testIndexDocumentEscapesFieldsWithQuotes() {
        // A field containing a single quote must not corrupt the request: it goes through
        // writeFile verbatim (no shell interpolation), proving the injection fix.
        script.sh = { Map args ->
            scriptArgs = args
            return args.script.contains('curl') ? '201' : 0
        }
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        metricsQuery.indexDocument("sampleIndex", [details: "5 issues don't have PR linked"])
        assertEquals('{"details":"5 issues don\'t have PR linked"}', writtenBody)
    }

    @Test
    void testIndexDocumentThrowsOnErrorStatus() {
        script.sh = { Map args ->
            scriptArgs = args
            return args.script.contains('curl') ? '404' : 0
        }
        def metricsQuery = new OpenSearchMetricsQuery("metricsUrl", "awsAccessKey", "awsSecretKey", "awsSessionToken", this.script)
        try {
            metricsQuery.indexDocument("sampleIndex", [version: '3.8.0'])
            fail("Expected RuntimeException when cluster returns a non-2xx status")
        } catch (RuntimeException e) {
            assertTrue(e.message.contains("Failed to index document into sampleIndex"))
        }
    }
}

