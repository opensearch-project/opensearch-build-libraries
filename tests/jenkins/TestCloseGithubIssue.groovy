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

class TestCloseGithubIssue extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
    }


    @Test
    void testCloseExistingGithubIssue() {
        this.registerLibTester(new CloseGithubIssueLibTester(
            'https://github.com/opensearch-project/opensearch-build',
            'Test GH issue title',
            'Test GH issue close comment'
            ))
        super.testPipeline("tests/jenkins/jobs/CloseGithubIssue_JenkinsFile")
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue list --repo https://github.com/opensearch-project/opensearch-build -S \"Test GH issue title in:title\" --json number --jq '.[0].number', returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue close bbb\nccc -R opensearch-project/opensearch-build --comment \"Test GH issue close comment\", returnStdout=true}"))
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
