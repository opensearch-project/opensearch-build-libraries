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
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckReleaseNotes extends BuildPipelineTest {

    // Latest metrics doc for the component reports release notes are missing (release_notes: false).
    def releaseNotesMissingResponse = '''
                {
                  "took": 4,
                  "timed_out": false,
                  "_shards": { "total": 5, "successful": 5, "skipped": 0, "failed": 0 },
                  "hits": {
                    "total": { "value": 1, "relation": "eq" },
                    "max_score": null,
                    "hits": [
                      {
                        "_index": "opensearch_release_metrics",
                        "_id": "86739a31-40db-320f-b52c-d38d50e179bc",
                        "_score": null,
                        "_source": { "release_notes": false },
                        "sort": [ 1738963520807 ]
                      }
                    ]
                  }
                }
                '''

    def releaseIssueResponse = '''
                {
                  "took": 5,
                  "timed_out": false,
                  "_shards": { "total": 5, "successful": 5, "skipped": 0, "failed": 0 },
                  "hits": {
                    "total": { "value": 1, "relation": "eq" },
                    "max_score": null,
                    "hits": [
                      {
                        "_index": "opensearch_release_metrics",
                        "_id": "fec68961-abdb-3e49-b97f-b656b0a9a510",
                        "_score": null,
                        "_source": { "release_issue": "https://github.com/opensearch-project/opensearch/issues/123" },
                        "sort": [ 1738963520807 ]
                      }
                    ]
                  }
                }
                '''

    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('sleep', [Map])
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('METRICS_HOST_ACCOUNT', "METRICS_HOST_ACCOUNT")
        binding.setVariable('GITHUB_USER', "GITHUB_USER")
        binding.setVariable('GITHUB_TOKEN', "GITHUB_TOKEN")
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
        // Pin the template's random file name so the notify callstack is deterministic (index 2 -> 'C').
        Random.metaClass.nextInt = { int max -> 2 }

        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET "sample.url/opensearch_release_metrics/_search" --aws-sigv4 "aws:amz:us-east-1:es" --user "abc:xyz" -H "x-amz-security-token:sampleToken" -H 'Content-Type: application/json' -d "{\\"size\\":1,\\"_source\\":\\"release_notes\\",\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}" | jq '.'\n        """) { script ->
            return [stdout: releaseNotesMissingResponse, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET "sample.url/opensearch_release_metrics/_search" --aws-sigv4 "aws:amz:us-east-1:es" --user "abc:xyz" -H "x-amz-security-token:sampleToken" -H 'Content-Type: application/json' -d "{\\"size\\":1,\\"_source\\":\\"release_issue\\",\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}" | jq '.'\n        """) { script ->
            return [stdout: releaseIssueResponse, exitValue: 0]
        }
    }

    @Test
    void testNotifyAction() {
        addParam('ACTION', 'notify')
        this.registerLibTester(new CheckReleaseNotesLibTester(['tests/data/opensearch-1.3.0.yml'], 'notify'))
        super.testPipeline('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
        def fileContent = getCommands('writeFile', 'release')[0]
        assertThat(fileContent, allOf(containsString("{file=/tmp/workspace/CCCCCCCCCC.md, text=Hi, </br>"),
                containsString("This component is missing release notes. Please add them on priority in order to meet the entrance criteria for the release. </br>")))
        assertThat(getCommands('echo', 'missing'), hasItem("Components missing release notes: [OpenSearch]"))
        assertThat(getCommands('sh', 'opensearch'), hasItem("{script=gh issue comment https://github.com/opensearch-project/opensearch/issues/123 --body-file /tmp/workspace/CCCCCCCCCC.md, returnStdout=true}"))
    }

    @Test
    void testCheckAction() {
        addParam('ACTION', 'check')
        this.registerLibTester(new CheckReleaseNotesLibTester(['tests/data/opensearch-1.3.0.yml'], 'check'))
        runScript('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
        assertThat(getCommands('echo', 'missing'), hasItem("Components missing release notes: [OpenSearch]"))
        // 'check' must not comment on the release issue.
        assertThat(getCommands('sh', 'gh issue comment').size(), equalTo(0))
    }

    @Test
    void testAllComponentsHaveReleaseNotes() {
        addParam('ACTION', 'check')
        def releaseNotesPresentResponse = '''
                {
                  "took": 4,
                  "timed_out": false,
                  "_shards": { "total": 5, "successful": 5, "skipped": 0, "failed": 0 },
                  "hits": {
                    "total": { "value": 1, "relation": "eq" },
                    "max_score": null,
                    "hits": [
                      {
                        "_index": "opensearch_release_metrics",
                        "_id": "86739a31-40db-320f-b52c-d38d50e179bc",
                        "_score": null,
                        "_source": { "release_notes": true },
                        "sort": [ 1738963520807 ]
                      }
                    ]
                  }
                }
                '''
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET "sample.url/opensearch_release_metrics/_search" --aws-sigv4 "aws:amz:us-east-1:es" --user "abc:xyz" -H "x-amz-security-token:sampleToken" -H 'Content-Type: application/json' -d "{\\"size\\":1,\\"_source\\":\\"release_notes\\",\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"version\\":\\"1.3.0\\"}},{\\"match_phrase\\":{\\"component.keyword\\":\\"OpenSearch\\"}}]}},\\"sort\\":[{\\"current_date\\":{\\"order\\":\\"desc\\"}}]}" | jq '.'\n        """) { script ->
            return [stdout: releaseNotesPresentResponse, exitValue: 0]
        }
        this.registerLibTester(new CheckReleaseNotesLibTester(['tests/data/opensearch-1.3.0.yml'], 'check'))
        runScript('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
        assertThat(getCommands('echo', 'missing'), hasItem("Components missing release notes: []"))
    }

    @Test
    void testMispelledAction() {
        addParam('ACTION', 'chek')
        this.registerLibTester(new CheckReleaseNotesLibTester(['tests/data/opensearch-1.3.0.yml'], 'chek'))
        runScript('tests/jenkins/jobs/CheckReleaseNotes_JenkinsFile')
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
