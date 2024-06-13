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
        this.registerLibTester(new CreateBuildFailureGithubIssueLibTester(["Error building OpenSearch, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component OpenSearch --snapshot", "Error building performance-analyzer, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0ed in the next build. This might have performance impact if it keeps failing. Run the javaToolchains task for more det.yml --component performance-analyzer", "Error building asynchronous-search, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component asynchronous-search", "Error building geospatial, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component geospatial", "Error building performance-analyzer, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component performance-analyzer"]))
        super.setUp()
    }

    @Test
    public void testCreateGithubIssue() {
        super.testPipeline('tests/jenkins/jobs/CreateBuildFailureGithubIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'script'), hasItem('{script=gh issue comment bbb\n' +
                'ccc --repo https://github.com/opensearch-project/performance-analyzer.git --body "***Received Error***: **Error building performance-analyzer, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0ed in the next build. This might have performance impact if it keeps failing. Run the javaToolchains task for more det.yml --component performance-analyzer**.\n' +
                '                      The distribution build for performance-analyzer has failed for version: 2.0.0.\n' +
                '                      Please see build log at www.example.com/job/build_url/32/display/redirect.\n' +
                '                      The failed build stage will be marked as unstable(!). Please see ./build.sh step for more details", returnStdout=true}'))
    }

    @Test
    public void testExistingGithubIssue_TestCreateBuildFailureGithubIssue() {
        super.testPipeline('tests/jenkins/jobs/CreateBuildFailureGithubIssue_Jenkinsfile', 'tests/jenkins/jobs/CreateBuildFailureGithubExistingIssueCheck_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('Issue already exists, adding a comment'))
        assertThat(getCommands('sh', 'script'), hasItem("""{script=gh issue comment bbb\nccc --repo https://github.com/opensearch-project/OpenSearch.git --body \"***Received Error***: **Error building OpenSearch, retry with: ./build.sh manifests/2.2.0/opensearch-2.2.0.yml --component OpenSearch --snapshot**.\n                      The distribution build for OpenSearch has failed for version: 2.0.0.\n                      Please see build log at www.example.com/job/build_url/32/display/redirect.\n                      The failed build stage will be marked as unstable(!). Please see ./build.sh step for more details\", returnStdout=true}"""))
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
