/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.Before
import org.junit.Test
import groovy.json.JsonOutput
import jenkins.ReleaseCandidateStatus


class TestReleaseCandidateStatus {
    private ReleaseCandidateStatus releaseCandidateStatus
    private final String metricsUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'
    private final String buildIndexName = 'opensearch-distribution-build-results'
    private final String version = "2.19.0"
    private final String qualifier = "None"
    private def script


    @Before
    void setUp() {
        releaseCandidateStatus = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, version, qualifier, script)
    }

    @Test
    void testGetRcDistributionNumberQuery() {
        String defaultExpectedOutput = JsonOutput.toJson([
                _source: "distribution_build_number",
                sort   : [
                        [
                                distribution_build_number: [
                                        order: "desc"
                                ],
                                rc_number                : [
                                        order: "desc"
                                ]
                        ]
                ],
                size   : 1,
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component: "OpenSearch"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        rc: "true"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${version}"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]).replace('"', '\\"')
        def defaultQueryResult = releaseCandidateStatus.getRcDistributionNumberQuery('OpenSearch')
        assert defaultExpectedOutput == defaultQueryResult

        String expectedOutputWithRcNumber = JsonOutput.toJson([
                _source: "distribution_build_number",
                sort   : [
                        [
                                distribution_build_number: [
                                        order: "desc"
                                ]
                        ]
                ],
                size   : 1,
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component: "OpenSearch"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        rc: "true"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        rc_number: "9"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]).replace('"', '\\"')
        def queryResultWithRc = releaseCandidateStatus.getRcDistributionNumberQuery(9, 'OpenSearch')
        assert queryResultWithRc == expectedOutputWithRcNumber
    }

    @Test
    void testGetLatestRcNumberQuery() {
        String expectedOutput = JsonOutput.toJson([
                _source: "rc_number",
                sort   : [
                        [
                                distribution_build_number: [
                                        order: "desc"
                                ],
                                rc_number                : [
                                        order: "desc"
                                ]
                        ]
                ],
                size   : 1,
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component: "OpenSearch"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        rc: "true"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]).replace('"', '\\"')
        def queryResult = releaseCandidateStatus.getLatestRcNumberQuery('OpenSearch')
        assert queryResult == expectedOutput
    }

    @Test
    void testGetLatestRcNumberQueryWithQualifier() {
        String expectedOutput = JsonOutput.toJson([
                _source: "rc_number",
                sort   : [
                        [
                                distribution_build_number: [
                                        order: "desc"
                                ],
                                rc_number                : [
                                        order: "desc"
                                ]
                        ]
                ],
                size   : 1,
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component: "OpenSearch"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        rc: "true"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        qualifier: "alpha1"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]).replace('"', '\\"')
        def releaseCandidateStatusNew = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, version, 'alpha1', script)
        def queryResult = releaseCandidateStatusNew.getLatestRcNumberQuery('OpenSearch')
        assert queryResult == expectedOutput
    }

    @Test
    void testGetRcDistributionNumber() {
        def responseText = """
                    {
                      "took": 16,
                      "timed_out": false,
                      "_shards": {
                        "total": 40,
                        "successful": 40,
                        "skipped": 0,
                        "failed": 0
                      },
                      "hits": {
                        "total": {
                          "value": 11,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch-distribution-build-results-02-2025",
                            "_id": "xAsw15QB2OP_jOaCGo2o",
                            "_score": null,
                            "_source": {
                              "distribution_build_number": 10787
                            },
                            "sort": [
                              10787,
                              5
                            ]
                          }
                        ]
                      }
                    }
                """
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return responseText
            }
        }
        ReleaseCandidateStatus releaseCandidateStatusOb = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, version, qualifier, script)
        def expectedOutput = 10787
        def result = releaseCandidateStatusOb.getRcDistributionNumber('OpenSearch')
        assert result == expectedOutput
    }

    @Test
    void testGetLatestRcNumber() {
        def responseText = """
                {
                  "took": 15,
                  "timed_out": false,
                  "_shards": {
                    "total": 40,
                    "successful": 40,
                    "skipped": 0,
                    "failed": 0
                  },
                  "hits": {
                    "total": {
                      "value": 11,
                      "relation": "eq"
                    },
                    "max_score": null,
                    "hits": [
                      {
                        "_index": "opensearch-distribution-build-results-02-2025",
                        "_id": "LDU115QBOi-lzDIlXPa2",
                        "_score": null,
                        "_source": {
                          "rc_number": 5
                        },
                        "sort": [
                          1738771668769,
                          5
                        ]
                      }
                    ]
                  }
                }
                """
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return responseText
            }
        }
        ReleaseCandidateStatus releaseCandidateStatusOb = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, version, qualifier, script)
        def expectedOutput = 5
        def result = releaseCandidateStatusOb.getLatestRcNumber('OpenSearch')
        assert result == expectedOutput

    }
}
