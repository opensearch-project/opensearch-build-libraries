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

class TestBuildRc extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod("withSecrets", [Map])
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
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
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

        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"overall_build_result\\":\\"SUCCESS\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osLatestRcResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch-Dashboards\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"overall_build_result\\":\\"SUCCESS\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osdLatestRcResponse, exitValue: 0]
        }
    }

    @Test
    void testBuildingBothRC() {
        addParam('PRODUCT', 'both')
        this.registerLibTester(new BuildRcLibTester('2.19.0'))
        super.testPipeline('tests/jenkins/jobs/BuildRc.jenkinsFile')
        def callStack = helper.getCallStack()
        assertCallStack().contains('BuildRc.buildRC({version=2.19.0, product=both})')
        assertCallStack().contains('buildRC.string({name=INPUT_MANIFEST, value=2.19.0/opensearch-2.19.0.yml})')
        assertCallStack().contains('buildRC.string({name=TEST_MANIFEST, value=2.19.0/opensearch-2.19.0-test.yml})')
        assertCallStack().contains('buildRC.string({name=BUILD_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=BUILD_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=TEST_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=TEST_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=RC_NUMBER, value=6})')
        assertCallStack().contains('buildRC.string({name=BUILD_DOCKER, value=build_docker_with_build_number_tag})')
        assertCallStack().contains('buildRC.booleanParam({name=CONTINUE_ON_ERROR, value=false})')
        assertCallStack().contains('buildRC.booleanParam({name=UPDATE_GITHUB_ISSUE, value=true})')
        assertCallStack().contains('buildRC.build({job=distribution-build-opensearch, parameters=[null, null, null, null, null, null, null, null, null, null], wait=false, propagate=false})')
        assertCallStack().contains('buildRC.string({name=INPUT_MANIFEST, value=2.19.0/opensearch-dashboards-2.19.0.yml})')
        assertCallStack().contains('buildRC.string({name=TEST_MANIFEST, value=2.19.0/opensearch-dashboards-2.19.0-test.yml})')
        assertCallStack().contains('buildRC.string({name=BUILD_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=BUILD_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=TEST_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=TEST_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=RC_NUMBER, value=6})')
        assertCallStack().contains('buildRC.string({name=BUILD_DOCKER, value=build_docker_with_build_number_tag})')
        assertCallStack().contains('buildRC.booleanParam({name=CONTINUE_ON_ERROR, value=false})')
        assertCallStack().contains('buildRC.booleanParam({name=UPDATE_GITHUB_ISSUE, value=true})')
        assertCallStack().contains('buildRC.build({job=distribution-build-opensearch-dashboards, parameters=[null, null, null, null, null, null, null, null, null, null], wait=false, propagate=false})')
        assertThat(getCommands('echo', 'Current'), hasItem('Retrieved Current RC numbers: OpenSearch - 5, OpenSearch-Dashboards - 5'))
        assertThat(getCommands('echo', 'Triggering'), hasItem('Triggering both OpenSearch and OpenSearch-Dashboards builds with RC numbers: 6, 6 respectively'))
    }

    @Test
    void testBuildingOpenSearchOnlyRC() {
        addParam('PRODUCT', 'opensearch')
        this.registerLibTester(new BuildRcLibTester('2.19.0', 'opensearch'))
        runScript('tests/jenkins/jobs/BuildRc.jenkinsFile')
        def callStack = helper.getCallStack()
        assertThat(getCommands('echo', 'Current'), hasItem('Retrieved Current RC numbers: OpenSearch - 5, OpenSearch-Dashboards - 5'))
        assertThat(getCommands('echo', 'triggering'), hasItem('Only triggering OpenSearch build with RC number: 6'))
        assertCallStack().contains('BuildRc.buildRC({version=2.19.0, product=opensearch})')
        assertCallStack().contains('buildRC.string({name=INPUT_MANIFEST, value=2.19.0/opensearch-2.19.0.yml})')
        assertCallStack().contains('buildRC.string({name=TEST_MANIFEST, value=2.19.0/opensearch-2.19.0-test.yml})')
        assertCallStack().contains('buildRC.string({name=BUILD_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=BUILD_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=TEST_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=TEST_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=RC_NUMBER, value=6})')
        assertCallStack().contains('buildRC.string({name=BUILD_DOCKER, value=build_docker_with_build_number_tag})')
        assertCallStack().contains('buildRC.booleanParam({name=CONTINUE_ON_ERROR, value=false})')
        assertCallStack().contains('buildRC.booleanParam({name=UPDATE_GITHUB_ISSUE, value=true})')
        assertCallStack().contains('buildRC.build({job=distribution-build-opensearch, parameters=[null, null, null, null, null, null, null, null, null, null], wait=false, propagate=false})')
    }

    @Test
    void testBuildingOsdOnlyRC() {
        addParam('PRODUCT', 'opensearch-dashboards')
        this.registerLibTester(new BuildRcLibTester('2.19.0', 'opensearch-dashboards'))
        runScript('tests/jenkins/jobs/BuildRc.jenkinsFile')
        def callStack = helper.getCallStack()
        assertThat(getCommands('echo', 'Current'), hasItem('Retrieved Current RC numbers: OpenSearch - 5, OpenSearch-Dashboards - 5'))
        assertThat(getCommands('echo', 'triggering'), hasItem('Only triggering OpenSearch-Dashboards build with RC number: 6'))
        assertCallStack().contains('buildRC.string({name=INPUT_MANIFEST, value=2.19.0/opensearch-dashboards-2.19.0.yml})')
        assertCallStack().contains('buildRC.string({name=TEST_MANIFEST, value=2.19.0/opensearch-dashboards-2.19.0-test.yml})')
        assertCallStack().contains('buildRC.string({name=BUILD_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=BUILD_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=TEST_PLATFORM, value=linux windows})')
        assertCallStack().contains('buildRC.string({name=TEST_DISTRIBUTION, value=tar rpm deb zip})')
        assertCallStack().contains('buildRC.string({name=RC_NUMBER, value=6})')
        assertCallStack().contains('buildRC.string({name=BUILD_DOCKER, value=build_docker_with_build_number_tag})')
        assertCallStack().contains('buildRC.booleanParam({name=CONTINUE_ON_ERROR, value=false})')
        assertCallStack().contains('buildRC.booleanParam({name=UPDATE_GITHUB_ISSUE, value=true})')
        assertCallStack().contains('buildRC.build({job=distribution-build-opensearch-dashboards, parameters=[null, null, null, null, null, null, null, null, null, null], wait=false, propagate=false})')
    }

    @Test
    void testWrongProduct() {
        addParam('PRODUCT', 'opensearch-dashboard')
        this.registerLibTester(new BuildRcLibTester('2.19.0', 'opensearch-dashboard'))
        runScript('tests/jenkins/jobs/BuildRc.jenkinsFile')
        assertThat(getCommands('error', ''), hasItem("Invalid product 'opensearch-dashboard'. Valid values: opensearch, opensearch-dashboards, both"))
        assertJobStatusFailure()
    }

    @Test
    void testFirstRC() {
        addParam('PRODUCT', 'both')
        this.registerLibTester(new BuildRcLibTester('2.19.0'))
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
                          "value": 0,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": []
                      }
                    }
                    '''

        def osdLatestRcResponse = '''
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
                          "value": 0,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": []
                      }
                    }
                    '''

        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"overall_build_result\\":\\"SUCCESS\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osLatestRcResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch-Dashboards\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"overall_build_result\\":\\"SUCCESS\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osdLatestRcResponse, exitValue: 0]
        }
        runScript('tests/jenkins/jobs/BuildRc.jenkinsFile')
        assertThat(getCommands('echo', 'Current'), hasItem('Retrieved Current RC numbers: OpenSearch - 0, OpenSearch-Dashboards - 0'))
        assertThat(getCommands('echo', 'Triggering'), hasItem('Triggering both OpenSearch and OpenSearch-Dashboards builds with RC numbers: 1, 1 respectively'))
        }

    @Test
    void testNullResponse() {
        addParam('PRODUCT', 'opensearch-dashboards')
        this.registerLibTester(new BuildRcLibTester('2.19.0', 'opensearch-dashboards'))
        def osdLatestRcResponse = 'Access issue'

        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":\\"rc_number\\",\\"sort\\":[{\\"distribution_build_number\\":{\\"order\\":\\"desc\\"},\\"rc_number\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1,\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"OpenSearch-Dashboards\\"}},{\\"match_phrase\\":{\\"rc\\":\\"true\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"overall_build_result\\":\\"SUCCESS\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: osdLatestRcResponse, exitValue: 0]
        }
        runScript('tests/jenkins/jobs/BuildRc.jenkinsFile')
        assertThat(getCommands('error', ''), hasItem("Unable to fetch latest RC number from metrics. Received null value."))
        assertJobStatusFailure()
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
