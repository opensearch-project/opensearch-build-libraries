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

class TestCreateBuildFailureGithubIssue extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        this.registerLibTester(new CreateBuildFailureGithubIssueLibTester(['Error building OpenSearch, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component OpenSearch --snapshot']))
        super.setUp()
    }

    @Test
    public void testCreateGithubIssue() {
        helper.addShMock('gh issue list --repo https://github.com/opensearch-project/OpenSearch.git -S "[AUTOCUT] Distribution Build Failed for OpenSearch-2.0.0 in:title" --label autocut,v2.0.0', '', 0)
        super.testPipeline('tests/jenkins/jobs/CreateBuildFailureGithubIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'create'), hasItem('{script=gh issue create --title \"[AUTOCUT] Distribution Build Failed for OpenSearch-2.0.0\" --body \"***Received Error***: **Error building OpenSearch, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component OpenSearch --snapshot**.\n                      The distribution build for OpenSearch has failed for version: 2.0.0.\n                      Please see build log at www.example.com/jobs/test/123/consoleFull\" --label autocut,v2.0.0 --label \"untriaged\" --repo https://github.com/opensearch-project/OpenSearch.git, returnStdout=true}'))
    }

    @Test
    public void testExistingGithubIssue() {
        super.testPipeline('tests/jenkins/jobs/CreateBuildFailureGithubIssue_Jenkinsfile', 'tests/jenkins/jobs/CreateBuildFailureGithubExistingIssueCheck_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Issue already exists, adding a comment.'))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue list --repo https://github.com/opensearch-project/OpenSearch.git -S \"[AUTOCUT] Distribution Build Failed for OpenSearch-2.0.0 in:title\" --label autocut,v2.0.0, returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue list --repo https://github.com/opensearch-project/OpenSearch.git -S \"[AUTOCUT] Distribution Build Failed for OpenSearch-2.0.0 in:title\" --label autocut,v2.0.0 --json number --jq '.[0].number', returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment bbb\nccc --repo https://github.com/opensearch-project/OpenSearch.git --body \"***Received Error***: **Error building OpenSearch, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component OpenSearch --snapshot**.\n                      The distribution build for OpenSearch has failed for version: 2.0.0.\n                      Please see build log at www.example.com/jobs/test/123/consoleFull\", returnStdout=true}"))
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
