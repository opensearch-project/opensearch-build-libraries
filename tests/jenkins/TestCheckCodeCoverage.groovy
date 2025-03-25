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
import jenkins.tests.CheckCodeCoverageLibTester
import jenkins.tests.CheckReleaseNotesLibTester
import org.junit.Before
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckCodeCoverage extends BuildPipelineTest {
    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('withCredentials', [Map])
        binding.setVariable('env', [
                'METRICS_HOST_URL'     : 'sample.url',
                'AWS_ACCESS_KEY_ID'    : 'abc',
                'AWS_SECRET_ACCESS_KEY': 'xyz',
                'AWS_SESSION_TOKEN'    : 'sampleToken'
        ])
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.addFileExistsMock('tests/data/opensearch-1.3.0.yml', true)
        def coverageResponse = '''
                {
                  "took": 3,
                  "timed_out": false,
                  "_shards": {
                    "total": 5,
                    "successful": 5,
                    "skipped": 0,
                    "failed": 0
                  },
                  "hits": {
                    "total": {
                      "value": 20,
                      "relation": "eq"
                    },
                    "max_score": null,
                    "hits": [
                      {
                        "_index": "opensearch-codecov-metrics-03-2025",
                        "_id": "5e8f90ec-983d-362c-8828-ecff1731f301",
                        "_score": null,
                        "_source": {
                          "coverage": 0,
                          "state": "no-coverage",
                          "branch": "1.3",
                          "url": "https://api.codecov.io/api/v2/github/opensearch-project/repos/OpenSearch/commits?branch=1.3"
                        },
                        "sort": [
                          1742505921205
                        ]
                      }
                    ]
                  }
                }
                    '''


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
                          "value": 10,
                          "relation": "eq"
                        },
                        "max_score": null,
                        "hits": [
                          {
                            "_index": "opensearch_release_metrics",
                            "_id": "fec68961-abdb-3e49-b97f-b656b0a9a510",
                            "_score": null,
                            "_source": {
                              "release_issue": "https://github.com/opensearch-project/OpenSearch/issues/5152"
                            },
                            "sort": [
                              1738866320789
                            ]
                          }
                        ]
                      }
                    }
'''
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-codecov-metrics-03-2025/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"coverage\\",\\"branch\\",\\"state\\",\\"url\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"repository.keyword\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: coverageResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":\\"release_issue\\",\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"repository\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: releaseIssueResponse, exitValue: 0]
        }
    }

    @Test
    void testNotifyAction() {
        addParam('ACTION', 'notify')
        Random.metaClass.nextInt = { int max -> 1 }
        this.registerLibTester(new CheckCodeCoverageLibTester(['tests/data/opensearch-1.3.0.yml'], 'notify'))
        super.testPipeline('tests/jenkins/jobs/CheckCodeCoverage_Jenkinsfile')
        def fileContent = getCommands('writeFile', 'code-coverage')[0]
        assertThat(fileContent, allOf(
                containsString("{file=/tmp/workspace/BBBBBBBBBB.md, text=Hi, </br>"),
                containsString("OpenSearch is not reporting code-coverage for branch [1.3](https://api.codecov.io/api/v2/github/opensearch-project/repos/OpenSearch/commits?branch=1.3). </br>"),
                containsString("Please fix the issue by checking your CI workflow responsible for reporting code coverage. See the details on [code coverage reporting](https://github.com/opensearch-project/opensearch-plugins/blob/main/TESTING.md#code-coverage-reporting) </br>")
        ))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment https://github.com/opensearch-project/OpenSearch/issues/5152 --body-file /tmp/workspace/BBBBBBBBBB.md, returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment https://github.com/opensearch-project/OpenSearch/issues/5152 --body-file /tmp/workspace/BBBBBBBBBB.md, returnStdout=true}"))
    }

    @Test
    void testCheckAction() {
        addParam('ACTION', 'check')
        def coverageResponse = '''
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
                      "value": 20,
                      "relation": "eq"
                    },
                    "max_score": null,
                    "hits": [
                      {
                        "_index": "opensearch-codecov-metrics-03-2025",
                        "_id": "5e8f90ec-983d-362c-8828-ecff1731f301",
                        "_score": null,
                        "_source": {
                          "coverage": 71.98,
                          "state": "complete",
                          "branch": "1.3",
                          "url": "https://api.codecov.io/api/v2/github/opensearch-project/repos/OpenSearch/commits?branch=1.3"
                        },
                        "sort": [
                          1742505921205
                        ]
                      }
                    ]
                  }
                }
                    '''
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-codecov-metrics-03-2025/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"coverage\\",\\"branch\\",\\"state\\",\\"url\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"repository.keyword\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: coverageResponse, exitValue: 0]
        }
        this.registerLibTester(new CheckCodeCoverageLibTester(['tests/data/opensearch-1.3.0.yml'], 'check'))
        runScript('tests/jenkins/jobs/CheckCodeCoverage_Jenkinsfile')
        assertThat(getCommands('echo', 'components'), hasItem("All components are reporting code coverage."))
    }

    @Test
    void testMispelledAction() {
        addParam('ACTION', 'chek')
        this.registerLibTester(new CheckCodeCoverageLibTester(['tests/data/opensearch-1.3.0.yml'], 'chek'))
        runScript('tests/jenkins/jobs/CheckCodeCoverage_Jenkinsfile')
        assertThat(getCommands('error', ''), hasItem("Invalid action 'chek'. Valid values: check, notify"))
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
