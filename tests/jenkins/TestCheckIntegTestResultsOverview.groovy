/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import jenkins.tests.BuildPipelineTest
import jenkins.tests.CheckIntegTestResultsOverviewLibTester
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckIntegTestResultsOverview extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('env', [
                'METRICS_HOST_URL'     : 'sample.url',
                'AWS_ACCESS_KEY_ID'    : 'abc',
                'AWS_SECRET_ACCESS_KEY': 'xyz',
                'AWS_SESSION_TOKEN'    : 'sampleToken'
        ])
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('METRICS_HOST_ACCOUNT', "METRICS_HOST_ACCOUNT")
        binding.setVariable('GITHUB_USER', "GITHUB_USER")
        binding.setVariable('GITHUB_TOKEN', "GITHUB_TOKEN")
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.addFileExistsMock('tests/data/opensearch-input-2.12.0.yml', true)
        helper.addFileExistsMock('tests/data/opensearch-dashboards-input-2.12.0.yml', true)
        def rcNumberResponseOS = '''
                     {
                      "took": 18,
                      "timed_out": false,
                      "_shards": {
                        "total": 50,
                        "successful": 50,
                        "skipped": 0,
                        "failed": 0
                      },
                      "hits": {
                        "total": {
                          "value": 5,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch-distribution-build-results-04-2025",
                            "_id": "QMRBX5YBhoDV_8niRPgm",
                            "_score": null,
                            "_source": {
                              "rc_number": 4
                            },
                            "sort": [
                              11026,
                              4
                            ]
                          }
                        ]
                      }
                    }
                    '''

        def rcNumberResponseOSD = '''
                     {
                      "took": 18,
                      "timed_out": false,
                      "_shards": {
                        "total": 50,
                        "successful": 50,
                        "skipped": 0,
                        "failed": 0
                      },
                      "hits": {
                        "total": {
                          "value": 5,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch-distribution-build-results-04-2025",
                            "_id": "QMRBX5YBhoDV_8niRPgm",
                            "_score": null,
                            "_source": {
                              "rc_number": 3
                            },
                            "sort": [
                              11026,
                              4
                            ]
                          }
                        ]
                      }
                    }
                    '''

        def osdComponentsResponse = '''
                        {
                          "took": 19,
                          "timed_out": false,
                          "_shards": {
                            "total": 50,
                            "successful": 50,
                            "skipped": 0,
                            "failed": 0
                          },
                          "hits": {
                            "total": {
                              "value": 29,
                              "relation": "eq"
                            },
                            "max_score": null,
                            "hits": [
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "ElzfX5YBIpIPk1eDTtsc",
                                "_score": null,
                                "_source": {
                                  "component": "observabilityDashboards",
                                  "component_build_result": "failed"
                                },
                                "fields": {
                                  "component": [
                                    "observabilityDashboards"
                                  ]
                                },
                                "sort": [
                                  1745361502088
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "aDZvX5YBOi-lzDIlR1XO",
                                "_score": null,
                                "_source": {
                                  "component": "OpenSearch-Dashboards-ci-group-2",
                                  "component_build_result": "failed"
                                },
                                "fields": {
                                  "component": [
                                    "OpenSearch-Dashboards-ci-group-2"
                                  ]
                                },
                                "sort": [
                                  1745355847608
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "f1yLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "OpenSearch-Dashboards-ci-group-6",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "OpenSearch-Dashboards-ci-group-6"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "jVyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "securityAnalyticsDashboards",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "securityAnalyticsDashboards"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              }
                            ]
                          }
                        }
                        '''
        def osComponentsResponse = '''
                        {
                          "took": 19,
                          "timed_out": false,
                          "_shards": {
                            "total": 50,
                            "successful": 50,
                            "skipped": 0,
                            "failed": 0
                          },
                          "hits": {
                            "total": {
                              "value": 29,
                              "relation": "eq"
                            },
                            "max_score": null,
                            "hits": [
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "ElzfX5YBIpIPk1eDTtsc",
                                "_score": null,
                                "_source": {
                                  "component": "cross-cluster-replication",
                                  "component_build_result": "failed"
                                },
                                "fields": {
                                  "component": [
                                    "cross-cluster-replication"
                                  ]
                                },
                                "sort": [
                                  1745361502088
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "aDZvX5YBOi-lzDIlR1XO",
                                "_score": null,
                                "_source": {
                                  "component": "neural-search",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "neural-search"
                                  ]
                                },
                                "sort": [
                                  1745355847608
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "f1yLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "alerting",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "alerting"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "glyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "asynchronous-search",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "asynchronous-search"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "hVyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "index-management",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "index-management"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "h1yLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "ml-commons",
                                  "component_build_result": "failed"
                                },
                                "fields": {
                                  "component": [
                                    "ml-commons"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "jVyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "security",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "security"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "flyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "OpenSearch",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "OpenSearch"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "hFyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "geospatial",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "geospatial"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "i1yLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "opensearch-observability",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "opensearch-observability"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "ilyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "notifications",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "notifications"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "jlyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "security-analytics",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "security-analytics"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "gFyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "anomaly-detection",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "anomaly-detection"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "jFyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "opensearch-reports",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "opensearch-reports"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "j1yLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "sql",
                                  "component_build_result": "failed"
                                },
                                "fields": {
                                  "component": [
                                    "sql"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "hlyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "k-NN",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "k-NN"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              },
                              {
                                "_index": "opensearch-integration-test-results-04-2025",
                                "_id": "klyLX5YBIpIPk1eDvNqs",
                                "_score": null,
                                "_source": {
                                  "component": "query-insights",
                                  "component_build_result": "passed"
                                },
                                "fields": {
                                  "component": [
                                    "query-insights"
                                  ]
                                },
                                "sort": [
                                  1745353345259
                                ]
                              }
                            ]
                          }
                        }
                        '''

        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.12.0\\"}},{\\"match_phrase\\":{\\"overall_build_result\\":\\"SUCCESS\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: rcNumberResponseOS, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch-Dashboards\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.12.0\\"}},{\\"match_phrase\\":{\\"overall_build_result\\":\\"SUCCESS\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: rcNumberResponseOSD, exitValue: 0]
        }

        Map<String, List> archDistMap = [
                "x64": ['tar', 'rpm', 'deb', 'zip'],
                "arm64": ['tar', 'rpm', 'deb']
        ]

        archDistMap.each {arch, distributions ->
            distributions.each { dist ->
                helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":100,\\"sort\\":[{\\"build_start_time\\":{\\"order\\":\\"desc\\"}}],\\"_source\\":[\\"component\\",\\"component_build_result\\"],\\"query\\":{\\"bool\\":{\\"must\\":[{\\"match_phrase\\":{\\"rc_number\\":\\"4\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.12.0\\"}},{\\"match_phrase\\":{\\"distribution\\":\\"${dist}\\"}},{\\"match_phrase\\":{\\"architecture\\":\\"${arch}\\"}},{\\"terms\\":{\\"component\\":[\\"index-management\\",\\"job-scheduler\\",\\"opensearch-reports\\",\\"ml-commons\\",\\"anomaly-detection\\",\\"common-utils\\",\\"neural-search\\",\\"cross-cluster-replication\\",\\"security-analytics\\",\\"asynchronous-search\\",\\"OpenSearch\\",\\"sql\\",\\"alerting\\",\\"security\\",\\"k-NN\\",\\"geospatial\\",\\"notifications-core\\",\\"notifications\\",\\"opensearch-observability\\"]}}]}},\\"collapse\\":{\\"field\\":\\"component\\"}}\" | jq '.'\n        """) { script ->
                    return [stdout: osComponentsResponse, exitValue: 0]
                }
                helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":100,\\"sort\\":[{\\"build_start_time\\":{\\"order\\":\\"desc\\"}}],\\"_source\\":[\\"component\\",\\"component_build_result\\"],\\"query\\":{\\"bool\\":{\\"must\\":[{\\"match_phrase\\":{\\"rc_number\\":\\"3\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.12.0\\"}},{\\"match_phrase\\":{\\"distribution\\":\\"${dist}\\"}},{\\"match_phrase\\":{\\"architecture\\":\\"${arch}\\"}},{\\"bool\\":{\\"should\\":[{\\"regexp\\":{\\"component\\":\\"OpenSearch-Dashboards-ci-group-.*\\"}},{\\"terms\\":{\\"component\\":[\\"OpenSearch-Dashboards\\",\\"functionalTestDashboards\\",\\"observabilityDashboards\\"]}}]}}]}},\\"collapse\\":{\\"field\\":\\"component\\"}}\" | jq '.'\n        """) { script ->
                    return [stdout: osdComponentsResponse, exitValue: 0]
                }
            } }

    }

    @Test
    void testFailedComponents() {
        this.registerLibTester(new CheckIntegTestResultsOverviewLibTester(['tests/data/opensearch-input-2.12.0.yml', 'tests/data/opensearch-dashboards-input-2.12.0.yml']))
        super.testPipeline('tests/jenkins/jobs/CheckIntegTestResultsOverview_Jenkinsfile')
        assertThat(getCommands('echo', 'Components failing integration tests'), hasItem("Components failing integration tests:\ntar_x64: [cross-cluster-replication, ml-commons, sql, observabilityDashboards, OpenSearch-Dashboards-ci-group-2]\nrpm_x64: [cross-cluster-replication, ml-commons, sql, observabilityDashboards, OpenSearch-Dashboards-ci-group-2]\ndeb_x64: [cross-cluster-replication, ml-commons, sql, observabilityDashboards, OpenSearch-Dashboards-ci-group-2]\nzip_x64: [cross-cluster-replication, ml-commons, sql, observabilityDashboards, OpenSearch-Dashboards-ci-group-2]\ntar_arm64: [cross-cluster-replication, ml-commons, sql, observabilityDashboards, OpenSearch-Dashboards-ci-group-2]\nrpm_arm64: [cross-cluster-replication, ml-commons, sql, observabilityDashboards, OpenSearch-Dashboards-ci-group-2]\ndeb_arm64: [cross-cluster-replication, ml-commons, sql, observabilityDashboards, OpenSearch-Dashboards-ci-group-2]"))
    }

    def getCommands(method, text) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == method
        }.collect { call ->
            callArgsToString(call)
        }.findAll { command ->
            command.contains(text)
        }
        return shCommands
    }
}
