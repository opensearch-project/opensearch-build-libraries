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

class TestCloseBuildSuccessGithubIssue extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        this.registerLibTester(new CloseBuildSuccessGithubIssueLibTester(['Build successful OpenSearch']))
        super.setUp()
    }


    @Test
    public void testExistingGithubIssue() {
        super.testPipeline('tests/jenkins/jobs/CloseBuildSuccessGithubIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue list --repo https://github.com/opensearch-project/OpenSearch.git -S \"[AUTOCUT] Distribution Build Failed for OpenSearch-2.0.0 in:title\" --label autocut,v2.0.0 --json number --jq '.[0].number', returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue close bbb\nccc -R opensearch-project/OpenSearch --comment \"Closing the issue as the distribution build for OpenSearch has passed for version: **2.0.0**.\n                    Please see build log at www.example.com/jobs/test/123/consoleFull\", returnStdout=true}"))
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
