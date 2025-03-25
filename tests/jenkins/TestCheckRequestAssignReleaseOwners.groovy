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
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckRequestAssignReleaseOwners extends BuildPipelineTest {

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
        helper.addFileExistsMock('tests/data/opensearch-1.3.0.yml', true)
        def releaseOwnerResponse = '''
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
                              "release_issue": "https://github.com/opensearch-project/opensearch/issues/123"
                            },
                            "sort": [
                              1738963520807
                            ]
                          }
                        ]
                      }
                    }
                    '''

        def getMaintainersResponse = '''
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
                '''


        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":[\\"release_owners\\",\\"release_issue_exists\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: releaseOwnerResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch_release_metrics/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":1,\\"_source\\":\\"release_issue\\",\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"repository\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: releaseIssueResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/maintainer-inactivity-03-2025/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"size\\":100,\\"_source\\":\\"github_login\\",\\"collapse\\":{\\"field\\":\\"github_login.keyword\\"},\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"repository.keyword\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"inactive\\":\\"false\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}\" | jq '.'\n        """) { script ->
            return [stdout: getMaintainersResponse, exitValue: 0]
        }
    }

    @Test
    void testCheckAction() {
        addParam('ACTION', 'check')
        this.registerLibTester(new CheckReleaseOwnersLibTester(['tests/data/opensearch-1.3.0.yml'], 'check'))
        super.testPipeline('tests/jenkins/jobs/CheckRequestAssignReleaseOwnerJenkinsFile')
        assertThat(getCommands('echo', 'missing'), hasItem("Components missing release owner: [OpenSearch]"))
    }

    @Test
    void testRequestAction() {
        addParam('ACTION', 'request')
        this.registerLibTester(new CheckReleaseOwnersLibTester(['tests/data/opensearch-1.3.0.yml'], 'request'))
        runScript('tests/jenkins/jobs/CheckRequestAssignReleaseOwnerJenkinsFile')
        assertThat(getCommands('sh', 'issue'), hasItem("{script=gh issue comment https://github.com/opensearch-project/opensearch/issues/123 --body-file /tmp/workspace/BBBBBBBBBB.md, returnStdout=true}"))
        def fileContent = getCommands('writeFile', 'release')[0]
        assertThat(fileContent, containsString("{file=/tmp/workspace/BBBBBBBBBB.md, text=Hi @foo, @bar, </br>"))
        assertThat(fileContent, containsString("Could someone kindly volunteer to take on the role of release owner for this component in order to meet the [entrance criteria](https://github.com/opensearch-project/.github/blob/main/RELEASING.md#entrance-criteria-to-start-release-window) ? </br>"))
        assertThat(fileContent, containsString("If no one is able to take it on, we may need to assign someone randomly before the release window opens. </br>"))
    }

    @Test
    void testAssignAction() {
        addParam('ACTION', 'assign')
        Random.metaClass.nextInt = { int max -> 1 }
        this.registerLibTester(new CheckReleaseOwnersLibTester(['tests/data/opensearch-1.3.0.yml'], 'assign'))
        runScript('tests/jenkins/jobs/CheckRequestAssignReleaseOwnerJenkinsFile')
        assertThat(getCommands('sh', 'issue'), hasItems("{script=gh issue comment https://github.com/opensearch-project/opensearch/issues/123 --body-file /tmp/workspace/BBBBBBBBBB.md, returnStdout=true}", "{script=gh issue edit https://github.com/opensearch-project/opensearch/issues/123 --add-assignee bar, returnStdout=true}"))
        def fileContent = getCommands('writeFile', 'release')[0]
        assertThat(fileContent, containsString("{file=/tmp/workspace/BBBBBBBBBB.md, text=Hi @bar, </br>"))
        assertThat(fileContent, containsString("Since this component currently does not have a release owner, we will assign you to this role for the time being! </br>"))
        assertThat(fileContent, containsString("If you feel this should be reassigned, please feel free to delegate it to the appropriate maintainer. </br>"))
    }

    @Test
    void testParameterCheck() {
        addParam('ACTION', 'asign')
        this.registerLibTester(new CheckReleaseOwnersLibTester(['tests/data/opensearch-1.3.0.yml'], 'asign'))
        runScript('tests/jenkins/jobs/CheckRequestAssignReleaseOwnerJenkinsFile')
        assertThat(getCommands('error', ''), hasItems("Invalid action 'asign'. Valid values: check, assign, request"))
    }

    @Test
    void testAssignmentFailure() {
        addParam('ACTION', 'assign')
        helper.addShMock("""gh issue comment https://github.com/opensearch-project/opensearch/issues/123 --body-file /tmp/workspace/BBBBBBBBBB.md""") { script ->
            return [stdout: "Wrong credentials", exitValue: 127]
        }
        this.registerLibTester(new CheckReleaseOwnersLibTester(['tests/data/opensearch-1.3.0.yml'], 'assign'))
        runScript('tests/jenkins/jobs/CheckRequestAssignReleaseOwnerJenkinsFile')
        assertThat(getCommands('error', ''), hasItem("Failed to assign release owner for OpenSearch: Script returned error code: 127"))
    }

    @Test
    void testMaintainerRequestFailure() {
        addParam('ACTION', 'request')
        helper.addShMock("""gh issue comment https://github.com/opensearch-project/opensearch/issues/123 --body-file /tmp/workspace/BBBBBBBBBB.md""") { script ->
            return [stdout: "Wrong credentials", exitValue: 127]
        }
        this.registerLibTester(new CheckReleaseOwnersLibTester(['tests/data/opensearch-1.3.0.yml'], 'request'))
        runScript('tests/jenkins/jobs/CheckRequestAssignReleaseOwnerJenkinsFile')
        assertThat(getCommands('error', ''), hasItem("Failed to request maintainers for OpenSearch: Script returned error code: 127"))
    }

    @Test
    void testForAssignedReleaseOwners() {
        addParam('ACTION', 'request')
        def releaseOwnerResponseFoo = '''
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
                                  "release_owners": ["foo"]
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
            return [stdout: releaseOwnerResponseFoo, exitValue: 0]
        }
        this.registerLibTester(new CheckReleaseOwnersLibTester(['tests/data/opensearch-1.3.0.yml'], 'request'))
        runScript('tests/jenkins/jobs/CheckRequestAssignReleaseOwnerJenkinsFile')
        assertThat(getCommands('echo', 'missing'), not(hasItem("Components missing release owner: [OpenSearch]")))
        assertThat(getCommands('echo', 'components'), hasItem("All components have release owner assigned."))
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
