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
import static org.hamcrest.MatcherAssert.assertThat
import com.lesfurets.jenkins.unit.*

class TestGradleCheckFlakyTestGitHubIssue extends BuildPipelineTest {

    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('GITHUB_USER', 'dummy_user')
        binding.setVariable('GITHUB_TOKEN', 'dummy_token')
    }

    @Test
    public void testDefaultIssueEdit() {
        super.testPipeline("tests/jenkins/jobs/TestGradleCheckFlakyTestGitHubIssue_Jenkinsfile", "tests/jenkins/jobs/TestGradleCheckFlakyTestGitHubIssueEdit_Jenkinsfile")
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/OpenSearch --body-file \"SampleTest.md\", returnStdout=true}"))
    }

    @Test
    public void testIssueCreate() {
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/OpenSearch -S "[AUTOCUT] Gradle Check Flaky Test Report for SampleTest in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/OpenSearch -S "[AUTOCUT] Gradle Check Flaky Test Report for SampleTest in:title is:closed" --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline("tests/jenkins/jobs/TestGradleCheckFlakyTestGitHubIssue_Jenkinsfile", "tests/jenkins/jobs/TestGradleCheckFlakyTestGitHubIssueCreate_Jenkinsfile")
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue create --title \"[AUTOCUT] Gradle Check Flaky Test Report for SampleTest\" --body-file \"SampleTest.md\" --label \"autocut,>test-failure,flaky-test\" --label \"untriaged\" --repo https://github.com/opensearch-project/OpenSearch, returnStdout=true}"))
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

