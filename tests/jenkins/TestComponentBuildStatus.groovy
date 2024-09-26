/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import org.junit.*
import groovy.json.JsonOutput
import groovy.mock.interceptor.MockFor

class TestComponentBuildStatus {

    private ComponentBuildStatus componentBuildStatus
    private final String metricsUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'
    private final String indexName = 'opensearch-distribution-build-results-*'
    private final String product = "OpenSearch"
    private final String version = "2.18.0"
    private final String distributionBuildNumber = "4891"
    private final String buildStartTimeFrom = "now-6h"
    private final String buildStartTimeTo = "now"
    private def script

    @Before
    void setUp() {
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return """
                {
                    "hits": {
                    "total": {
                    "value": 2,
                    "relation": "eq"
                    },
                    "max_score": 0,
                    "hits": [
                    {
                        "_index": "opensearch-distribution-build-results-09-2024",
                        "_id": "QTVbQZIBOi-lzDIlekCk",
                        "_score": 0,
                        "_source": {
                        "component": "performance-analyzer"
                        }
                    },
                    {
                        "_index": "opensearch-distribution-build-results-09-2024",
                        "_id": "PzVbQZIBOi-lzDIlekCk",
                        "_score": 0,
                        "_source": {
                        "component": "security-analytics"
                        }
                    }
                    ]
                }
                }
                """
            }
            return ""
        }
        componentBuildStatus = new ComponentBuildStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, product, version, distributionBuildNumber, buildStartTimeFrom, buildStartTimeTo, script)
    }

    @Test
    void testGetQueryReturnsExpectedQuery() {
        def expectedOutput = JsonOutput.toJson([
                _source: [
                        "component",
                        ], 
                query: [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component_category: "OpenSearch"
                                                        ]
                                                ],
                                        [
                                                match_phrase: [
                                                        component_build_result: "failed"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "2.18.0"
                                                ]
                                        ],
                                        [
                                                match_phrase : [
                                                        distribution_build_number : "4891"
                                                ]
                                        ],
                                        [
                                                range: [
                                                        build_start_time: [
                                                                from: "now-6h",
                                                                to: "now"
                                                                ]
                                                        ]
                                        ]
                                ]
                        ]
                ]
        ]).replace('"', '\\"')

        def result = componentBuildStatus.getQuery('failed')
        assert result == expectedOutput
    }

    @Test
    void testComponentBuildStatusReturns() {
        def expectedOutput = ['performance-analyzer', 'security-analytics']
        def result = componentBuildStatus.getComponents('failed')

        assert result == expectedOutput
    }
}
