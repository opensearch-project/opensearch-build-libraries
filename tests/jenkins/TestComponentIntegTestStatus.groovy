/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import jenkins.ComponentIntegTestStatus
import org.junit.Before
import org.junit.Test
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class TestComponentIntegTestStatus {

    private ComponentIntegTestStatus componentIntegTestStatus
    private final String metricsUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'
    private final String indexName = 'opensearch-integration-test-results'
    private final String product = "OpenSearch"
    private final String version = "2.18.0"
    private final String qualifier = "None"
    private final String distributionBuildNumber = "4891"
    private def script

    @Before
    void setUp() {
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return """
                    {
                    "took": 10,
                    "timed_out": false,
                    "_shards": {
                        "total": 20,
                        "successful": 20,
                        "skipped": 0,
                        "failed": 0
                    },
                    "hits": {
                        "total": {
                        "value": 35,
                        "relation": "eq"
                        },
                        "max_score": 0,
                        "hits": [
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "Tw1IS5IBpSkIYPznAxki",
                            "_score": 0,
                            "_source": {
                            "component": "cross-cluster-replication"
                            }
                        },
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "Ug1IS5IBpSkIYPznAxki",
                            "_score": 0,
                            "_source": {
                            "component": "k-NN"
                            }
                        },
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "pltLS5IBIpIPk1eDbs5_",
                            "_score": 0,
                            "_source": {
                            "component": "cross-cluster-replication"
                            }
                        },
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "qFtLS5IBIpIPk1eDbs5_",
                            "_score": 0,
                            "_source": {
                            "component": "index-management"
                            }
                        },
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "q1tLS5IBIpIPk1eDbs5_",
                            "_score": 0,
                            "_source": {
                            "component": "neural-search"
                            }
                        }
                        ]
                    }
                    }
                """
            }
            return ""
        }
        componentIntegTestStatus = new ComponentIntegTestStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, product, version, qualifier, distributionBuildNumber, script)
    }

    @Test
    void testGetQueryReturnsExpectedQuery() {
        def expectedOutput = JsonOutput.toJson([
                size: 50,
                _source: [
                        "component",
                        ],
                query: [
                        bool: [
                                filter: [
                                    [
                                        match_phrase: [
                                            version : "2.18.0"
                                        ]
                                    ],
                                    [
                                        match_phrase: [
                                            component_category: "OpenSearch"
                                        ]
                                    ],
                                    [
                                        match_phrase: [
                                            distribution_build_number : "4891"
                                        ]
                                    ],
                                    [
                                        match_phrase: [
                                            component_build_result: "failed"
                                        ]
                                    ]
                                ]
                        ]
                ]
        ]).replace('"', '\\"')

        def result = componentIntegTestStatus.getQuery('failed')
        assert result == expectedOutput
    }

    @Test
    void testComponentIntegTestFailedDataQuery() {
        def expectedOutput = JsonOutput.toJson([
            _source : [
                "platform",
                "architecture",
                "distribution",
                "test_report_manifest_yml",
                "integ_test_build_url",
                "rc_number"
            ],
            query: [
                bool: [
                    filter: [
                        [
                            match_phrase: [
                                component: "k-NN"
                            ]
                        ],
                        [
                            match_phrase: [
                                version: "2.18.0"
                            ]
                        ],
                        [
                            match_phrase: [
                                distribution_build_number: "4891"
                            ]
                        ]
                    ]
                ]
            ]
        ]).replace('"', '\\"')
        def result = componentIntegTestStatus.componentIntegTestFailedDataQuery('k-NN')
        assert result == expectedOutput
    }

    @Test
    void testComponentIntegTestFailedDataQueryWithQualifier() {
        def expectedOutput = JsonOutput.toJson([
                _source : [
                        "platform",
                        "architecture",
                        "distribution",
                        "test_report_manifest_yml",
                        "integ_test_build_url",
                        "rc_number"
                ],
                query: [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component: "k-NN"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "2.18.0"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        distribution_build_number: "4891"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        qualifier: "beta1"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]).replace('"', '\\"')
        def componentIntegTestStatusNew = new ComponentIntegTestStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, product, version, 'beta1', distributionBuildNumber, script)
        def result = componentIntegTestStatusNew.componentIntegTestFailedDataQuery('k-NN')
        assert result == expectedOutput
    }

    @Test
    void testGetComponents() {
        def expectedOutput = ['cross-cluster-replication', 'k-NN', 'cross-cluster-replication', 'index-management', 'neural-search']
        def result = componentIntegTestStatus.getComponents('failed')

        assert result == expectedOutput
    }

    @Test
    void testGetComponentIntegTestFailedData() {
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return """
                    {
                    "took": 5,
                    "timed_out": false,
                    "_shards": {
                        "total": 20,
                        "successful": 20,
                        "skipped": 0,
                        "failed": 0
                    },
                    "hits": {
                        "total": {
                        "value": 2,
                        "relation": "eq"
                        },
                        "max_score": 0,
                        "hits": [
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "wArzVZIB2OP_jOaCFPPY",
                            "_score": 0,
                            "_source": {
                            "test_report_manifest_yml": "https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml",
                            "integ_test_build_url": "https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6561/display/redirect",
                            "distribution": "tar",
                            "platform": "linux",
                            "architecture": "x64"
                            }
                        },
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "jVsOVpIBIpIPk1eDrdI3",
                            "_score": 0,
                            "_source": {
                            "test_report_manifest_yml": "https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml",
                            "integ_test_build_url": "https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6560/display/redirect",
                            "distribution": "tar",
                            "platform": "linux",
                            "architecture": "arm64"
                            }
                        }
                        ]
                    }
                    }
                """
            }
            return ""
        }
        componentIntegTestStatus = new ComponentIntegTestStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, product, version, qualifier, distributionBuildNumber, script)
        def componentData = '''
                    {
                    "took": 5,
                    "timed_out": false,
                    "_shards": {
                        "total": 20,
                        "successful": 20,
                        "skipped": 0,
                        "failed": 0
                    },
                    "hits": {
                        "total": {
                        "value": 2,
                        "relation": "eq"
                        },
                        "max_score": 0,
                        "hits": [
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "wArzVZIB2OP_jOaCFPPY",
                            "_score": 0,
                            "_source": {
                            "test_report_manifest_yml": "https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml",
                            "integ_test_build_url": "https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6561/display/redirect",
                            "distribution": "tar",
                            "platform": "linux",
                            "architecture": "x64"
                            }
                        },
                        {
                            "_index": "opensearch-integration-test-results-10-2024",
                            "_id": "jVsOVpIBIpIPk1eDrdI3",
                            "_score": 0,
                            "_source": {
                            "test_report_manifest_yml": "https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml",
                            "integ_test_build_url": "https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6560/display/redirect",
                            "distribution": "tar",
                            "platform": "linux",
                            "architecture": "arm64"
                            }
                        }
                        ]
                    }
                    }
        '''
        def expectedOutput = new JsonSlurper().parseText(componentData)
        def result = componentIntegTestStatus.getComponentIntegTestFailedData('observabilityDashboards')

        assert result == expectedOutput
    }

}
