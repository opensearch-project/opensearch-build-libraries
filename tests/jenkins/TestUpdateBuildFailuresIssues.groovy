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

class TestUpdateBuildFailuresIssues extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        this.registerLibTester(new UpdateBuildFailureIssuesLibTester('tests/data/opensearch-2.2.0.yml', '32'))
        super.setUp()
        def unformattedResponseForPass = '''
        {
        "took": 5,
        "timed_out": false,
        "_shards": {
            "total": 15,
            "successful": 15,
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
                "_index": "opensearch-distribution-build-results-09-2024",
                "_id": "OsNSLJIBhoDV_8nijeJt",
                "_score": 0,
                "_source": {
                "component": "sql"
                }
            },
            {
                "_index": "opensearch-distribution-build-results-09-2024",
                "_id": "LsNSLJIBhoDV_8nijeJt",
                "_score": 0,
                "_source": {
                "component": "notifications"
                }
            }
            ]
        }
        }
        '''
        def unformattedResponseForFail = '''
        {
        "took": 5,
        "timed_out": false,
        "_shards": {
            "total": 15,
            "successful": 15,
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
                "_index": "opensearch-distribution-build-results-09-2024",
                "_id": "OsNSLJIBhoDV_8nijeJt",
                "_score": 0,
                "_source": {
                "component": "asynchronous-search"
                }
            },
            {
                "_index": "opensearch-distribution-build-results-09-2024",
                "_id": "LsNSLJIBhoDV_8nijeJt",
                "_score": 0,
                "_source": {
                "component": "notifications"
                }
            }
            ]
        }
        }
        '''
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":[\\"component\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component_category\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"component_build_result\\":\\"passed\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"32\\"}},{\\"range\\":{\\"build_start_time\\":{\\"from\\":\\"now-6h\\",\\"to\\":\\"now\\"}}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: unformattedResponseForPass, exitValue: 0]
        }
        helper.addShMock("""\n            set -e\n            set +x\n            curl -s -XGET \"sample.url/opensearch-distribution-build-results/_search\" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"abc:xyz\" -H \"x-amz-security-token:sampleToken\" -H 'Content-Type: application/json' -d \"{\\"_source\\":[\\"component\\"],\\"query\\":{\\"bool\\":{\\"filter\\":[{\\"match_phrase\\":{\\"component_category\\":\\"OpenSearch\\"}},{\\"match_phrase\\":{\\"component_build_result\\":\\"failed\\"}},{\\"match_phrase\\":{\\"version\\":\\"2.2.0\\"}},{\\"match_phrase\\":{\\"distribution_build_number\\":\\"32\\"}},{\\"range\\":{\\"build_start_time\\":{\\"from\\":\\"now-6h\\",\\"to\\":\\"now\\"}}}]}}}\" | jq '.'\n        """) { script ->
            return [stdout: unformattedResponseForFail, exitValue: 0]
        }
    }

    @Test
    public void testGithubIssueEdit() {
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/asynchronous-search.git -S "[AUTOCUT] Distribution Build Failed for asynchronous-search-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/asynchronous-search.git -S "[AUTOCUT] Distribution Build Failed for asynchronous-search-2.2.0 in:title is:closed closed:>=2023-10-24" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/UpdateBuildFailureIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Issue already exists, editing the issue body'))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/asynchronous-search.git --body \"***Build Failed Error***: **asynchronous-search failed during the distribution build for version: 2.2.0.**\n                    Please see build log at www.example.com/job/build_url/32/display/redirect.\n                    The failed build stage will be marked as unstable :warning: . Please see ./build.sh step for more details.\n                    Checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Building-an-OpenSearch-and-OpenSearch-Dashboards-Distribution) to reproduce the failure locally.\", returnStdout=true}"))
    }

    @Test
    public void testClosingGithubIssueOnSuccess() {
        helper.addShMock("date -d \"3 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/sql.git -S "[AUTOCUT] Distribution Build Failed for sql-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "30", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateBuildFailureIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'sql'), hasItem("{script=gh issue list --repo https://github.com/opensearch-project/sql.git -S \"[AUTOCUT] Distribution Build Failed for sql-2.2.0 in:title\" --json number --jq '.[0].number', returnStdout=true}"))
        assertThat(getCommands('sh', 'sql'), hasItem("{script=gh issue close bbb\nccc -R opensearch-project/sql --comment \"Closing the issue as the distribution build for sql has passed for version: **2.2.0**.\n                    Please see build log at www.example.com/job/build_url/32/display/redirect\", returnStdout=true}"))
    }

    @Test
    public void testNotClosingGithubIssueOnOneFailure() {
        helper.addShMock("date -d \"3 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/notifications.git -S "[AUTOCUT] Distribution Build Failed for notifications-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "20", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateBuildFailureIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'notifications'), not(hasItem("{script=gh issue close bbb\nccc -R opensearch-project/notifications --comment \"Closing the issue as the distribution build for notifications has passed for version: **2.2.0**.\n                    Please see build log at www.example.com/job/build_url/32/display/redirect\", returnStdout=true}")))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/notifications.git --body \"***Build Failed Error***: **notifications failed during the distribution build for version: 2.2.0.**\n                    Please see build log at www.example.com/job/build_url/32/display/redirect.\n                    The failed build stage will be marked as unstable :warning: . Please see ./build.sh step for more details.\n                    Checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Building-an-OpenSearch-and-OpenSearch-Dashboards-Distribution) to reproduce the failure locally.\", returnStdout=true}"))
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
