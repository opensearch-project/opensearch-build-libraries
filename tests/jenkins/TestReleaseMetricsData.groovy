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
import groovy.json.JsonSlurper

class TestReleaseMetricsData {
    private ReleaseMetricsData releaseMetricsData
    private final String metricsUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'
    private final String version = "2.18.0"
    private def script

    @Before
    void setUp() {
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return """
                    {
                      "took": 4,
                      "timed_out": false,
                      "_shards": {
                        "total": 5,
                        "successful": 5,
                        "skipped": 0,
                        "failed": 0
                      },
                      "hits": {
                        "total": {
                          "value": 31,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch_release_metrics",
                            "_id": "9ee464d8-b47d-3f5e-aa8f-8768c98a8d69",
                            "_score": null,
                            "_source": {
                              "release_owners": [
                                "foo",
                                "bar"
                              ]
                            },
                            "sort": [
                              1729707921551
                            ]
                          }
                        ]
                      }
                    }
                """
            }
            return ""
        }
        releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, 'opensearch_release_metrics', version, script)
    }

    @Test
    void testGetReleaseOwnerReturnQuery(){
        String expectedOutput = JsonOutput.toJson([
                size   : 1,
                _source: "release_owners",
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        version: "2.18.0"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        "component": "sql"
                                                ]
                                        ]
                                ]
                        ]
                ],
                sort : [
                        [
                                current_date: [
                                        order: "desc"
                                ]
                        ]
                ]
        ]).replace('"', '\\"')

        def result = releaseMetricsData.getReleaseOwnerQuery('sql')
        assert result == expectedOutput
    }

    @Test
    void testGetReleaseOwners(){
        def expectedOutput = ['foo', 'bar']
        def result = releaseMetricsData.getReleaseOwners('sql')
        assert  result == expectedOutput
    }
}
