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

class TestOpenSearchMetricsQuery {
    def script
    def scriptArgs
    String response

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
}

