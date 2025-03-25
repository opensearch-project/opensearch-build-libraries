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
import jenkins.tests.CheckReleaseNotesLibTester
import org.junit.Before
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckReleaseNotes extends BuildPipelineTest {
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
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        String testData = new File('tests/data/release-notes-check.md').text
        helper.addFileExistsMock('tests/data/release-notes-check.md', true)
        helper.addReadFileMock('tests/data/release-notes-check.md', testData)
        Random.metaClass.nextInt = { int max -> 2 }

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
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET "sample.url/opensearch_release_metrics/_search" --aws-sigv4 "aws:amz:us-east-1:es" --user "abc:xyz" -H "x-amz-security-token:sampleToken" -H 'Content-Type: application/json' -d "{\\"size\\":1,\\"_source\\":\\"release_issue\\",\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"2.19.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}" | jq '.'\n        """) { script ->
            return [stdout: releaseIssueResponse, exitValue: 0]
        }
    }


    @Test
    void testNotifyAction() {
        addParam('ACTION', 'notify')
        this.registerLibTester(new CheckReleaseNotesLibTester('2.19.0', 'tests/data/release-notes-check.md', 'notify'))
        super.testPipeline('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
        def fileContent = getCommands('writeFile', 'release')[0]
        assertThat(fileContent, allOf(containsString("{file=/tmp/workspace/CCCCCCCCCC.md, text=Hi, </br>"),
        containsString("This component is missing release notes at [main] ref. Please add them on priority in order to meet the entrance criteria for the release. </br>")))
        assertThat(getCommands('echo', 'missing'), hasItem("Components missing release notes: [OpenSearch, functionalTestDashboards]"))
        assertThat(getCommands('sh', 'opensearch'), hasItem("{script=gh issue comment https://github.com/opensearch-project/opensearch/issues/123 --body-file /tmp/workspace/CCCCCCCCCC.md, returnStdout=true}"))
        assertThat(getCommands('sh', 'functionalTestDashboards').size(), equalTo(0))
    }

    @Test
    void testCheckAction() {
        addParam('ACTION', 'check')
        this.registerLibTester(new CheckReleaseNotesLibTester('2.19.0', 'tests/data/release-notes-check.md', 'check'))
        runScript('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
        assertThat(getCommands('echo', 'missing'), hasItem("Components missing release notes: [OpenSearch, functionalTestDashboards]"))
    }

    @Test
    void testMispelledAction() {
        addParam('ACTION', 'chek')
        this.registerLibTester(new CheckReleaseNotesLibTester('2.19.0', 'tests/data/release-notes-check.md', 'chek'))
        runScript('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
        assertThat(getCommands('error', ''), hasItem("Invalid action 'chek'. Valid values: check, notify"))
        assertJobStatusFailure()
    }

    @Test
    void testParsingError() {
        addParam('ACTION', 'check')
        helper.addReadFileMock('tests/data/release-notes-check.md', "testData")
        this.registerLibTester(new CheckReleaseNotesLibTester('2.19.0', 'tests/data/release-notes-check.md', 'check'))
        runScript('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
        assertThat(getCommands('error', ''), hasItem("Unable to parse the release notes markdown table: fromIndex = -1"))
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
