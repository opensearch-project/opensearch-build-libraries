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

class TestUpdateBuildFailuresIssues extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        this.registerLibTester(new UpdateBuildFailureIssuesLibTester(["Error building common-utils, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component common-utils", "Error building performance-analyzer, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0ed in the next build. This might have performance impact if it keeps failing. Run the javaToolchains task for more det.yml --component performance-analyzer", "Error building asynchronous-search, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component asynchronous-search", "Error building asynchronous-search, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component asynchronous-search", "Error building anomaly-detection, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component anomaly-detection", "Error building performance-analyzer, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component performance-analyzer", "Error building notifications, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component notifications"], ["Successfully built sql", "Successfully built notifications", "Successfully built notifications", "Successfully built sql", "Successfully built anomaly-detection", "Successfully built index-management", "Successfully built sql", "Successfully built anomaly-detection"], 'tests/data/opensearch-2.2.0.yml'))
        super.setUp()
    }

    @Test
    public void testCreateGithubIssue() {
        helper.addShMock("date -d \"3 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }

        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/asynchronous-search.git -S "[AUTOCUT] Distribution Build Failed for asynchronous-search-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/asynchronous-search.git -S "[AUTOCUT] Distribution Build Failed for asynchronous-search-2.2.0 in:title is:closed closed:>=2023-10-24" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/UpdateBuildFailureIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'create'), hasItem('{script=gh issue create --title \"[AUTOCUT] Distribution Build Failed for asynchronous-search-2.2.0\" --body \"***Received Error***: **Error building asynchronous-search, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component asynchronous-search**.\n                    asynchronous-search failed during the distribution build for version: 2.2.0.\n                    Please see build log at www.example.com/job/build_url/32/display/redirect.\n                    The failed build stage will be marked as unstable(!). Please see ./build.sh step for more details\" --label autocut,v2.2.0 --label \"untriaged\" --repo https://github.com/opensearch-project/asynchronous-search.git, returnStdout=true}'))
    }

    @Test
    public void testCommentOnExistingGithubIssue() {
        helper.addShMock("date -d \"3 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/asynchronous-search.git -S "[AUTOCUT] Distribution Build Failed for asynchronous-search-2.2.0 in:title" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/asynchronous-search.git -S "[AUTOCUT] Distribution Build Failed for asynchronous-search-2.2.0 in:title is:closed closed:>=2023-10-24" --label autocut,v2.2.0 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateBuildFailureIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Issue already exists, adding a comment'))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment 22 --repo https://github.com/opensearch-project/asynchronous-search.git --body \"***Received Error***: **Error building asynchronous-search, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component asynchronous-search**.\n                    asynchronous-search failed during the distribution build for version: 2.2.0.\n                    Please see build log at www.example.com/job/build_url/32/display/redirect.\n                    The failed build stage will be marked as unstable(!). Please see ./build.sh step for more details\", returnStdout=true}"))
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
        assertThat(getCommands('sh', 'sql'), hasItem("{script=gh issue list --repo https://github.com/opensearch-project/sql.git -S \"[AUTOCUT] Distribution Build Failed for sql-2.2.0 in:title\" --label autocut,v2.2.0 --json number --jq '.[0].number', returnStdout=true}"))
        assertThat(getCommands('sh', 'sql'), hasItem("{script=gh issue close 30 -R opensearch-project/sql --comment \"Closing the issue as the distribution build for sql has passed for version: **2.2.0**.\n                    Please see build log at www.example.com/job/build_url/32/display/redirect\", returnStdout=true}"))
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
        assertThat(getCommands('sh', 'notifications'), not(hasItem("{script=gh issue close 20 -R opensearch-project/notifications --comment \"Closing the issue as the distribution build for notifications has passed for version: **2.2.0**.\n                    Please see build log at www.example.com/job/build_url/32/display/redirect\", returnStdout=true}")))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment 20 --repo https://github.com/opensearch-project/notifications.git --body \"***Received Error***: **Error building notifications, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component notifications**.\n                    notifications failed during the distribution build for version: 2.2.0.\n                    Please see build log at www.example.com/job/build_url/32/display/redirect.\n                    The failed build stage will be marked as unstable(!). Please see ./build.sh step for more details\", returnStdout=true}"))
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
