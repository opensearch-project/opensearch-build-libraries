/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.*
import groovy.json.JsonOutput
import jenkins.ComponentRepoData

class TestComponentRepoData {
    private ComponentRepoData componentRepoData
    private final String metricsUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'
    private final String version = "2.18.0"
    private final String maintainersIndexName = 'maintainer-inactivity-03-2025'
    private def script

    @Before
    void setUp() {
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return """
                    {
                      "took": 13,
                      "timed_out": false,
                      "_shards": {
                        "total": 25,
                        "successful": 25,
                        "skipped": 0,
                        "failed": 0
                      },
                      "hits": {
                        "total": {
                          "value": 3045,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "maintainer-inactivity-03-2025",
                            "_id": "c45967b3-9f0b-3b5d-aaa8-45b36876077b",
                            "_score": null,
                            "_source": {
                              "github_login": "foo"
                            },
                            "fields": {
                              "github_login.keyword": [
                                "foo"
                              ]
                            },
                            "sort": [
                              1740873626925
                            ]
                          },
                          {
                            "_index": "maintainer-inactivity-03-2025",
                            "_id": "2fa5f6a5-fe79-30ab-bb08-7ac837e55a01",
                            "_score": null,
                            "_source": {
                              "github_login": "bar"
                            },
                            "fields": {
                              "github_login.keyword": [
                                "bar"
                              ]
                            },
                            "sort": [
                              1740873626925
                            ]
                          }
                        ]
                      }
                    }
                """
            }
            return ""
        }
        componentRepoData = new ComponentRepoData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, script)
    }

    @Test
    void testGetMaintainersQuery(){
        String expectedOutput = JsonOutput.toJson([
                size   : 100,
                _source: "github_login",
                collapse: [
                        field: "github_login.keyword"
                ],
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        "repository.keyword": "sql"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        "inactive": "true"
                                                ]
                                        ]
                                ]
                        ]
                ],
                sort   : [
                        [
                                current_date: [
                                        order: "desc"
                                ]
                        ]
                ]
        ]).replace('"', '\\"')

        def result = componentRepoData.getMaintainersQuery('sql', true)
        assert result == expectedOutput
    }

    @Test
    void testGetMaintainers(){
        def expectedOutput = ['foo', 'bar']
        def result = componentRepoData.getMaintainers('sql', maintainersIndexName)
        assert  result == expectedOutput
    }

    @Test
    void testGetMaintainersException() {
        script = new Expando()
        script.println = { String message ->
            assert message.startsWith("Error fetching the maintainers for sql:")
        }
        componentRepoData = new ComponentRepoData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, script)
        componentRepoData.metaClass.openSearchMetricsQuery = [
                fetchMetrics: { query ->
                    throw new RuntimeException("Test exception")
                }
        ]
        def result = componentRepoData.getMaintainers('sql', maintainersIndexName )
        assert result == null
    }

    @Test
    void testGetCodeCoverageQuery() {
        String expectedOutput = JsonOutput.toJson([
                size   : 1,
                _source: [
                        "coverage",
                        "branch",
                        "state",
                        "url"
                ],
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        "repository.keyword": "OpenSearch"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        "version": "${this.version}"
                                                ]
                                        ]
                                ]
                        ]
                ],
                sort   : [
                        [
                                current_date: [
                                        order: "desc"
                                ]
                        ]
                ]
        ]).replace('"', '\\"')
        def result = componentRepoData.getCodeCoverageQuery('OpenSearch')
        assert result == expectedOutput
    }

    @Test
    void testGetCodeCoverage() {
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
                          "value": 22,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch-codecov-metrics-03-2025",
                            "_id": "a23e3d82-d0fa-3904-989d-5eb6b6a38efc",
                            "_score": null,
                            "_source": {
                              "coverage": 71.98,
                              "state": "complete",
                              "branch": "2.18",
                              "url": "https://api.codecov.io/api/v2/github/opensearch-project/repos/OpenSearch/commits?branch=2.18"
                            },
                            "sort": [
                              1742603121408
                            ]
                          }
                        ]
                      }
                    }
                """
            }
            return ""
        }
        componentRepoData = new ComponentRepoData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, script)
        def expectedOutput = [
                coverage: 71.98,
                state: "complete",
                branch: "2.18",
                url: "https://api.codecov.io/api/v2/github/opensearch-project/repos/OpenSearch/commits?branch=2.18"
        ]
        def result = componentRepoData.getCodeCoverage('OpenSearch', 'opensearch-code-coverage-metrics')
        assert  result == expectedOutput
    }

    @Test
    void testGetCodeCoverageException() {
        script = new Expando()
        script.println = { String message ->
            assert message.startsWith("Error fetching code coverage metrics for OpenSearch:")
        }
        componentRepoData = new ComponentRepoData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, script)
        componentRepoData.metaClass.openSearchMetricsQuery = [
                fetchMetrics: { query ->
                    throw new RuntimeException("Test exception")
                }
        ]
        def result = componentRepoData.getCodeCoverage('OpenSearch', 'opensearch-code-coverage-metrics')
        assert result == null
    }
}
