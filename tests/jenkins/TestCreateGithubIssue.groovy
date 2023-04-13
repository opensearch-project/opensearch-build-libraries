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

class TestCreateGithubIssue extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
    }

    @Test
    void testCreateGithubIssue() {
        this.registerLibTester(new CreateGithubIssueLibTester(
            'https://github.com/opensearch-project/opensearch-build',
            'Test GH issue title',
            'Test GH issue body',
            'label101'
            ))
        helper.addShMock('gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --label label101', '', 0)
        super.testPipeline('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'create'), hasItem('{script=gh issue create --title \"Test GH issue title\" --body \"Test GH issue body\" --label label101 --label \"untriaged\" --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}'))
    }

    @Test
    void testExistingGithubIssueAndAutocutLabel() {
        this.registerLibTester(new CreateGithubIssueLibTester(
            'https://github.com/opensearch-project/opensearch-build',
            'Test GH issue title',
            'Test GH issue body'
            ))
        super.testPipeline('tests/jenkins/jobs/CreateGithubIssueExisting_JenkinsFile')
        assertThat(getCommands('println', ''), hasItem('Issue already exists in the repository, skipping.'))
        assertThat(getCommands('sh', 'script'), hasItem('{script=gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --label autocut, returnStdout=true}'))
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
