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
import org.junit.Before
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestAddRcDetailsComment extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('withCredentials', [Map])
        helper.registerAllowedMethod('sleep', [Map])
        binding.setVariable('env', [
                'METRICS_HOST_URL'     : 'sample.url',
                'AWS_ACCESS_KEY_ID'    : 'abc',
                'AWS_SECRET_ACCESS_KEY': 'xyz',
                'AWS_SESSION_TOKEN'    : 'sampleToken'
        ])
        helper.registerAllowedMethod('withCredentials', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        def releaseIssueResponse = '''
                    {
                    
                      "took": 5,
                      "timed_out": false,
                      "_shards": {
                        "total": 5,
                        "successful": 5,
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
                            "_index": "opensearch_release_metrics",
                            "_id": "86739a31-40db-320f-b52c-d38d50e179bc",
                            "_score": null,
                            "_source": {
                              "release_issue": "https://github.com/opensearch-project/opensearch-build/issues/5152"
                            },
                            "sort": [
                              1738963520807
                            ]
                          }
                        ]
                      }
                    }
                    '''

        def osLatestRcResponse = '''
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
                              "rc_number": 5
                            },
                            "sort": [
                              10787,
                              5
                            ]
                          }
                        ]
                      }
                    }
                    '''

        def osdLatestRcResponse = '''
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
                          "value": 14,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch-distribution-build-results-02-2025",
                            "_id": "5MSv3pQBhoDV_8ni75zx",
                            "_score": null,
                            "_source": {
                              "rc_number": 5
                            },
                            "sort": [
                              8260,
                              5
                            ]
                          }
                        ]
                      }
                    }
                    '''

        def osRcDistributionNumberResponse = '''
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
                          "value": 1,
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
                              10787
                            ]
                          }
                        ]
                      }
                    }
                    '''

        def osdRcDistributionNumberResponse = '''
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
                          "value": 4,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch-distribution-build-results-02-2025",
                            "_id": "5MSv3pQBhoDV_8ni75zx",
                            "_score": null,
                            "_source": {
                              "distribution_build_number": 8260
                            },
                            "sort": [
                              8260
                            ]
                          }
                        ]
                      }
                    }
                    '''

        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":\\"release_issue\\",\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"repository\\":\\"opensearch-build\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: releaseIssueResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osLatestRcResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch-Dashboards\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osdLatestRcResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"distribution_build_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"rc_number\\":\\"5\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osRcDistributionNumberResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"distribution_build_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch-Dashboards\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"rc_number\\":\\"5\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osdRcDistributionNumberResponse, exitValue: 0]
        }

        helper.addShMock("""curl -s -XGET "https://build.ci.opensearch.org/blue/rest/organizations/jenkins/pipelines/distribution-build-opensearch/runs/10787/nodes/" | jq '.[] | select(.actions[].description? | contains("docker-scan")) | .actions[] | select(.description | contains("docker-scan")) | ._links.self.href'""") { script ->
            return [stdout: '/blue/rest/organizations/jenkins/pipelines/docker-scan/runs/4439/', exitValue: 0]
        }

        helper.addShMock("""curl -s -XGET "https://build.ci.opensearch.org/blue/rest/organizations/jenkins/pipelines/docker-scan/runs/4439/" | jq -r '._links.artifacts.href'""") { script ->
            return [stdout: '/blue/rest/organizations/jenkins/pipelines/docker-scan/runs/4439/artifacts/', exitValue: 0]
        }

        helper.addShMock("""curl -s -XGET "https://build.ci.opensearch.org/blue/rest/organizations/jenkins/pipelines/docker-scan/runs/4439/artifacts/" | jq -r '.[] | select(.name | endswith(".txt")) | .url'""") { script ->
            return [stdout: '/job/docker-scan/4439/artifact/scan_docker_image.txt', exitValue: 0]
        }

        helper.addShMock('curl -s -XGET "https://build.ci.opensearch.org/job/docker-scan/4439/artifact/scan_docker_image.txt"') { script ->
            return [stdout: 'Total: 0 (UNKNOWN: 0, LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0))', exitValue: 0]
        }
        Random.metaClass.nextInt = { int max -> 1 }
    }

    @Test
    void testAddingComment() {
        this.registerLibTester(new AddRcDetailsCommentLibTester('2.19.0'))
        super.testPipeline('tests/jenkins/jobs/AddRcDetailsComment.jenkinsFile')
        assertThat(getCommands('sh', 'comment'), hasItem("{script=gh issue comment https://github.com/opensearch-project/opensearch-build/issues/5152 --body-file /tmp/workspace/BBBBBBBBBB.md, returnStdout=true}"))
    }

    @Test
    void testCommentContent() {
        this.registerLibTester(new AddRcDetailsCommentLibTester('2.19.0'))
        runScript('tests/jenkins/jobs/AddRcDetailsComment.jenkinsFile')
        def fileContent = getCommands('writeFile', 'OpenSearch')[0]
        assertThat(fileContent, containsString("{file=/tmp/workspace/BBBBBBBBBB.md, text=## See OpenSearch RC 5 and OpenSearch-Dashboards RC 5 details"))
        assertThat(fileContent, containsString("OpenSearch 10787 and OpenSearch-Dashboards 8260 is ready for your test."))
        assertThat(fileContent, containsString("image: opensearchstaging/opensearch:2.19.0.1078"))
        assertThat(fileContent, containsString("image: opensearchstaging/opensearch-dashboards:2.19.0.8260"))
        assertThat(fileContent, containsString("Total: 0 (UNKNOWN: 0, LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0)"))
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
