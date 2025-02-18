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
                            "architecture": "x64",
                            "rc_number": 0
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
                            "architecture": "arm64",
                            "rc_number": 0
                            }
                        }
                        ]
                    }
                    }
        '''

        def latestDistributionBuildNumberResponse = '''
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

        def unformattedResponseForReleaseOwners = '''
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
        '''

        def unformattedResponseForReleaseOwnersGeo = '''
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
                          "release_owners": []
                        },
                        "sort": [
                          1729707921551
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
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":[\\"platform\\",\\"architecture\\",\\"distribution\\",\\"test_report_manifest_yml\\",\\"integ_test_build_url\\",\\"rc_number\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"geospatial\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"4891\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: failedTestDataResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-integration-test-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":[\\"platform\\",\\"architecture\\",\\"distribution\\",\\"test_report_manifest_yml\\",\\"integ_test_build_url\\",\\"rc_number\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component\\":\\"k-NN\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"4891\\"}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: failedTestDataResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"distribution_build_number\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component_category\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}}]}},\\"sort\\":[{\\"build_start_time\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: latestDistributionBuildNumberResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"release_owners\\",\\"release_issue_exists\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"k-NN\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: unformattedResponseForReleaseOwners, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"release_owners\\",\\"release_issue_exists\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"geospatial\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: unformattedResponseForReleaseOwnersGeo, exitValue: 0]
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
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/k-NN.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Dist | Arch | Dist Build No. | RC | Test Report | Workflow Run | Failing tests |\n|----------|------|------|----------------|----|-------------|--------------|---------------|\n| linux | tar | x64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:ddafb9c5-2d35-482a-9c61-1ba78b67f406,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:k-NN),type:phrase),query:(match_phrase:(component:k-NN)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n| linux | tar | arm64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:c570bdfd-3122-4e31-a02d-2130d797d9fc,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:k-NN),type:phrase),query:(match_phrase:(component:k-NN)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\nTagging the release owners to take a look @foo @bar\", returnStdout=true}"))
    }

    @Test
    public void testGithubIssueEditWithNoReleaseOwner() {
        this.registerLibTester(new UpdateIntegTestFailureIssuesLibTester('tests/data/opensearch-2.2.0.yml', '4891'))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/geospatial.git -S "[AUTOCUT] Integration Test Failed for geospatial-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/geospatial.git -S "[AUTOCUT] Integration Test Failed for geospatial-2.2.0 in:title is:closed closed:>=2023-10-24" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateIntegTestFailureIssues_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Issue already exists, editing the issue body'))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/geospatial.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Dist | Arch | Dist Build No. | RC | Test Report | Workflow Run | Failing tests |\n|----------|------|------|----------------|----|-------------|--------------|---------------|\n| linux | tar | x64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:ddafb9c5-2d35-482a-9c61-1ba78b67f406,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:geospatial),type:phrase),query:(match_phrase:(component:geospatial)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n| linux | tar | arm64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:c570bdfd-3122-4e31-a02d-2130d797d9fc,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:geospatial),type:phrase),query:(match_phrase:(component:geospatial)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\", returnStdout=true}"))
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
      assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/geospatial.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Dist | Arch | Dist Build No. | RC | Test Report | Workflow Run | Failing tests |\n|----------|------|------|----------------|----|-------------|--------------|---------------|\n| linux | tar | x64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:ddafb9c5-2d35-482a-9c61-1ba78b67f406,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:geospatial),type:phrase),query:(match_phrase:(component:geospatial)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n| linux | tar | arm64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:c570bdfd-3122-4e31-a02d-2130d797d9fc,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:geospatial),type:phrase),query:(match_phrase:(component:geospatial)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\", returnStdout=true}"))
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
       assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/k-NN.git --body \"\n### Integration Test Failed for version 2.2.0. See the specifications below:\n\n#### Details\n\n| Platform | Dist | Arch | Dist Build No. | RC | Test Report | Workflow Run | Failing tests |\n|----------|------|------|----------------|----|-------------|--------------|---------------|\n| linux | tar | x64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6561/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:ddafb9c5-2d35-482a-9c61-1ba78b67f406,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:k-NN),type:phrase),query:(match_phrase:(component:k-NN)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n| linux | tar | arm64 | 4891 | 0 | https://ci.opensearch.org/ci/dbc/integ-test/2.2.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test/6560/display/redirect | [Check metrics](https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:c570bdfd-3122-4e31-a02d-2130d797d9fc,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.2.0'),type:phrase),query:(match_phrase:(version:'2.2.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:k-NN),type:phrase),query:(match_phrase:(component:k-NN)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)) |\n\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).\n\nTagging the release owners to take a look @foo @bar\", returnStdout=true}"))
   }

   @Test
   public void verifyMetricsUrls(){
    def script = loadScript('vars/updateIntegTestFailureIssues.groovy')
    def version = '2.18.0'
    def component = 'security'

    def tar_x64 = script.getMetricsVisualizationUrl('tar', 'x64', version, component)
    assert tar_x64 == "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:ddafb9c5-2d35-482a-9c61-1ba78b67f406,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.18.0'),type:phrase),query:(match_phrase:(version:'2.18.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:security),type:phrase),query:(match_phrase:(component:security)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    def tar_arm64 = script.getMetricsVisualizationUrl('tar', 'arm64', version, component)
    assert tar_arm64 == "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:c570bdfd-3122-4e31-a02d-2130d797d9fc,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.18.0'),type:phrase),query:(match_phrase:(version:'2.18.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:security),type:phrase),query:(match_phrase:(component:security)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    def deb_x64 = script.getMetricsVisualizationUrl('deb', 'x64', version, component)
    assert deb_x64 == "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:5743d5c4-be75-49b9-a81f-fef3f805ad99,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.18.0'),type:phrase),query:(match_phrase:(version:'2.18.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:security),type:phrase),query:(match_phrase:(component:security)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    def deb_arm64 = script.getMetricsVisualizationUrl('deb', 'arm64', version, component)
    assert deb_arm64 == "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:7a6ee111-1c99-4f96-9a3f-c0f248181980,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.18.0'),type:phrase),query:(match_phrase:(version:'2.18.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:security),type:phrase),query:(match_phrase:(component:security)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    def rpm_x64 = script.getMetricsVisualizationUrl('rpm', 'x64', version, component)
    assert rpm_x64 == "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:94f0246f-4246-4f05-ba11-b3e22836b8e7,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.18.0'),type:phrase),query:(match_phrase:(version:'2.18.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:security),type:phrase),query:(match_phrase:(component:security)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    def rpm_arm64 = script.getMetricsVisualizationUrl('rpm', 'arm64', version, component)
    assert rpm_arm64 == "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:eae6bad4-cffc-4672-a688-14155229ea63,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.18.0'),type:phrase),query:(match_phrase:(version:'2.18.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:security),type:phrase),query:(match_phrase:(component:security)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    def windows_x64 = script.getMetricsVisualizationUrl('windows', 'x64', version, component)
    assert windows_x64 == "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',expandedPanelId:a57afb35-8d97-4641-9b07-64ff614dab00,filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'2.18.0'),type:phrase),query:(match_phrase:(version:'2.18.0'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:security),type:phrase),query:(match_phrase:(component:security)))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    def unknown_dist = script.getMetricsVisualizationUrl('de', 'x64', version, component)
    assert unknown_dist == "null"

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
