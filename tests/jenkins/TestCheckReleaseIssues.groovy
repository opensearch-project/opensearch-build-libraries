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
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckReleaseIssues extends BuildPipelineTest {

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
        def response = '''
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
                              "value": 30,
                              "relation": "eq"
                            },
                            "max_score": null,
                            "hits": [
                              {
                                "_index": "opensearch_release_metrics",
                                "_id": "76527151-e5e4-35bf-9763-3617d2898b82",
                                "_score": null,
                                "_source": {
                                  "release_issue_exists": false,
                                  "release_owners": []
                                },
                                "sort": [
                                  1740605121281
                                ]
                              }
                            ]
                          }
                        }
                    '''


        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"release_owners\\",\\"release_issue_exists\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: response, exitValue: 0]
        }
    }

    @Test
    void testMissingComponentsList() {
        this.registerLibTester(new CheckReleaseIssuesLibTester('2.19.0', ['tests/data/opensearch-1.3.0.yml'], 'create'))
        super.testPipeline('tests/jenkins/jobs/CheckReleaseIssues_Jenkinsfile')
        assertThat(getCommands('echo', 'missing'), hasItem("Components missing release issues: [OpenSearch]"))
    }

    @Test
    void testGHtriggerCall() {
        this.registerLibTester(new CheckReleaseIssuesLibTester('2.19.0', ['tests/data/opensearch-1.3.0.yml'], 'create'))
        runScript('tests/jenkins/jobs/CheckReleaseIssues_Jenkinsfile')
        assertThat(getCommands('sh', 'workflow'), hasItem("{script=gh workflow run os-release-issues.yml -R opensearch-project/opensearch-build && gh workflow run osd-release-issues.yml -R opensearch-project/opensearch-build, returnStdout=true}"))
    }

    @Test
    void testGHtriggerCallError() {
        this.registerLibTester(new CheckReleaseIssuesLibTester('2.19.0', ['tests/data/opensearch-1.3.0.yml'], 'create'))
        helper.addShMock("""gh workflow run os-release-issues.yml -R opensearch-project/opensearch-build && gh workflow run osd-release-issues.yml -R opensearch-project/opensearch-build""") { script ->
            return [stdout: 'script returned exit code 1', exitValue: 1]
        }
        runScript('tests/jenkins/jobs/CheckReleaseIssues_Jenkinsfile')
        assertThat(getCommands('error', 'GitHub'), hasItem("Error in triggering GitHub Actions workflows. Script returned error code: 1"))
    }

    @Test
    void testCreateActionIsNotCalled() {
        this.registerLibTester(new CheckReleaseIssuesLibTester('2.19.0', ['tests/data/opensearch-1.3.0.yml'], 'create'))
        def response = '''
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
                              "value": 30,
                              "relation": "eq"
                            },
                            "max_score": null,
                            "hits": [
                              {
                                "_index": "opensearch_release_metrics",
                                "_id": "76527151-e5e4-35bf-9763-3617d2898b82",
                                "_score": null,
                                "_source": {
                                  "release_issue_exists": true,
                                  "release_owners": []
                                },
                                "sort": [
                                  1740605121281
                                ]
                              }
                            ]
                          }
                        }
                    '''


        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"release_owners\\",\\"release_issue_exists\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: response, exitValue: 0]
        }
        runScript('tests/jenkins/jobs/CheckReleaseIssues_Jenkinsfile')
        assertThat(getCommands('sh', 'workflow'), not(hasItem("{script=gh workflow run os-release-issues.yml -R opensearch-project/opensearch-build && gh workflow run osd-release-issues.yml -R opensearch-project/opensearch-build, returnStdout=true}")))}

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
