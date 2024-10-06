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
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat
import groovy.json.JsonSlurper
import utils.OpenSearchMetricsQuery

class TestUpdateIntegTestFailureIssues extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('withCredentials', [Map])
        helper.registerAllowedMethod('sleep', [Map])
        binding.setVariable('env', [
            'METRICS_HOST_URL': 'sample.url',
            'AWS_ACCESS_KEY_ID': 'abc',
            'AWS_SECRET_ACCESS_KEY':'xyz',
            'AWS_SESSION_TOKEN': 'sampleToken'
            ])
        helper.registerAllowedMethod('withCredentials', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        def unformattedResponseForPass = '''
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
        '''
        def unformattedResponseForFail = '''
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
                                    "component": "geospatial"
                                }
                            },
                            {
                                "_index": "opensearch-integration-test-results-10-2024",
                                "_id": "Ug1IS5IBpSkIYPznAxki",
                                "_score": 0,
                                "_source": {
                                    "component": "k-NN"
                                }
                            }
                        ]
                    }
                }
        '''
        def failedTestDataResponse = '''
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
                            "test_report_manifest_yml": "https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml",
                            "integ_test_build_url": "https://build.ci.opensearch.org/job/integ-test/6561/display/redirect",
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
                            "test_report_manifest_yml": "https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml",
                            "integ_test_build_url": "https://build.ci.opensearch.org/job/integ-test/6560/display/redirect",
                            "distribution": "tar",
                            "platform": "linux",
                            "architecture": "arm64"
                            }
                        }
                        ]
                    }
                    }
        '''

        def latestDistributionBuildNumberReponse = '''
                    {
                    "took": 9,
                    "timed_out": false,
                    "_shards": {
                        "total": 20,
                        "successful": 20,
                        "skipped": 0,
                        "failed": 0
                    },
                    "hits": {
                        "total": {
                        "value": 245,
                        "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                        {
                            "_index": "opensearch-distribution-build-results-10-2024",
                            "_id": "mYJqVZIB2pK3vw3OS9GJ",
                            "_score": null,
                            "_source": {
                            "distribution_build_number": 4891
                            },
                            "sort": [
                            1728006178106
                            ]
                        }
                        ]
                    }
                    }
        '''
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":50,\\"_source\\":[\\"component\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"component_category\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"4891\\"}},{\\"match_phrase\\":{\\"component_build_result\\":\\"passed\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: unformattedResponseForPass, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":50,\\"_source\\":[\\"component\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"component_category\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"4891\\"}},{\\"match_phrase\\":{\\"component_build_result\\":\\"failed\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: unformattedResponseForFail, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":[\\"platform\\",\\"architecture\\",\\"distribution\\",\\"test_report_manifest_yml\\",\\"integ_test_build_url\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"geospatial\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"4891\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: failedTestDataResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":[\\"platform\\",\\"architecture\\",\\"distribution\\",\\"test_report_manifest_yml\\",\\"integ_test_build_url\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"k-NN\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"4891\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: failedTestDataResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"distribution_build_number\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component_category\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}}]}},\\"sort\\":[{\\"build_start_time\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: latestDistributionBuildNumberReponse, exitValue: 0]
        }
    }

    @Test
    public void testIssueCreation() {
        this.registerLibTester(new UpdateIntegTestFailureIssuesLibTester('tests/data/opensearch-2.2.0.yml', '4891'))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/k-NN.git -S "[AUTOCUT] Integration Test Failed for k-NN-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/k-NN.git -S "[AUTOCUT] Integration Test Failed for k-NN-2.2.0 in:title is:closed closed:>=2023-10-24" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/UpdateIntegTestFailureIssues_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Integration test failed for k-NN, creating github issue'))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/k-NN.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Distribution | Architecture | Test Report Manifest | Workflow Run |\n|----------|--------------|--------------|----------------------|--------------|\n| linux | tar | x64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect\n| linux | tar | arm64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\", returnStdout=true}"))
    }

    @Test
    public void testGithubIssueEdit() {
        this.registerLibTester(new UpdateIntegTestFailureIssuesLibTester('tests/data/opensearch-2.2.0.yml', '4891'))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/geospatial.git -S "[AUTOCUT] Integration Test Failed for geospatial-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/geospatial.git -S "[AUTOCUT] Integration Test Failed for geospatial-2.2.0 in:title is:closed closed:>=2023-10-24" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateIntegTestFailureIssues_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Issue already exists, editing the issue body'))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/geospatial.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Distribution | Architecture | Test Report Manifest | Workflow Run |\n|----------|--------------|--------------|----------------------|--------------|\n| linux | tar | x64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect\n| linux | tar | arm64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\", returnStdout=true}"))
    }

    @Test
    public void testClosingGithubIssueOnSuccess() {
        this.registerLibTester(new UpdateIntegTestFailureIssuesLibTester('tests/data/opensearch-2.2.0.yml', '4891'))
        helper.addShMock("date -d \"3 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/cross-cluster-replication.git -S "[AUTOCUT] Integration Test Failed for cross-cluster-replication-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "30", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateIntegTestFailureIssues_Jenkinsfile')
        assertThat(getCommands('sh', 'cross-cluster-replication'), hasItem("{script=gh issue list --repo https://github.com/opensearch-project/cross-cluster-replication.git -S \"[AUTOCUT] Integration Test Failed for cross-cluster-replication-2.2.0 in:title\" --json number --jq '.[0].number', returnStdout=true}"))
        assertThat(getCommands('sh', 'cross-cluster-replication'), hasItem("{script=gh issue close bbb\nccc -R opensearch-project/cross-cluster-replication --comment \"Closing the issue as the integration tests for cross-cluster-replication passed for version: **2.2.0**.\", returnStdout=true}"))
    }

    @Test
    public void testNotClosingGithubIssueOnOneFailure() {
        this.registerLibTester(new UpdateIntegTestFailureIssuesLibTester('tests/data/opensearch-2.2.0.yml', '4891'))
        helper.addShMock("date -d \"3 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/k-NN.git -S "[AUTOCUT] Integration Test Failed for k-NN-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "20", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateIntegTestFailureIssues_Jenkinsfile')
        assertThat(getCommands('sh', 'k-NN'), not(hasItem("{script=gh issue close bbb\nccc -R opensearch-project/k-NN --comment \"Closing the issue as the integration tests for k-NN passed for version: **2.2.0**.\", returnStdout=true}")))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/geospatial.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Distribution | Architecture | Test Report Manifest | Workflow Run |\n|----------|--------------|--------------|----------------------|--------------|\n| linux | tar | x64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect\n| linux | tar | arm64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\", returnStdout=true}"))
    }

    @Test
    public void testIssueCreationWithoutDistributionID() {
        this.registerLibTester(new UpdateIntegTestFailureIssuesLibTester('tests/data/opensearch-2.2.0.yml'))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/k-NN.git -S "[AUTOCUT] Integration Test Failed for k-NN-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/k-NN.git -S "[AUTOCUT] Integration Test Failed for k-NN-2.2.0 in:title is:closed closed:>=2023-10-24" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/UpdateBuildFailureIssue_without_distributionID_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Integration test failed for k-NN, creating github issue'))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/k-NN.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Distribution | Architecture | Test Report Manifest | Workflow Run |\n|----------|--------------|--------------|----------------------|--------------|\n| linux | tar | x64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect\n| linux | tar | arm64 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\", returnStdout=true}"))
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
