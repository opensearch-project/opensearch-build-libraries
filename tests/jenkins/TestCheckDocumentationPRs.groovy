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
import jenkins.tests.CheckDocumentationPullRequestsLibTester
import org.junit.Before
import org.junit.Test


class TestCheckDocumentationPullRequests extends BuildPipelineTest {
    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('GITHUB_USER', "GITHUB_USER")
        binding.setVariable('GITHUB_TOKEN', "GITHUB_TOKEN")
        addParam('VERSION', '3.0.0-beta1')
    }

    @Test
    void testGitCommand() {
        helper.addShMock("""gh pr list --repo opensearch-project/documentation-website --state open --label v3.0.0 -S "-label:\\"6 - Done but waiting to merge\\"" --json url --jq '.[].url'""") { script ->
            return [stdout: "https://github.com/opensearch-project/documentation-website/pull/123", exitValue: 0]
        }
        this.registerLibTester(new CheckDocumentationPullRequestsLibTester('3.0.0-beta1'))
        super.testPipeline('tests/jenkins/jobs/CheckDocumentationPRs_Jenkinsfile')
        def callStack = helper.getCallStack()
        assertCallStack().contains('checkDocumentationPullRequests.sh({script=gh pr list --repo opensearch-project/documentation-website --state open --label v3.0.0 -S "-label:\\"6 - Done but waiting to merge\\"" --json url --jq \'.[].url\', returnStdout=true})')
        assertCallStack().contains('checkDocumentationPullRequests.echo(Documentation pull requests pending to be merged: \n' +
                'https://github.com/opensearch-project/documentation-website/pull/123)')
    }

    @Test
    void testNoPrPending() {
        helper.addShMock("""gh pr list --repo opensearch-project/documentation-website --state open --label v3.0.0 -S "-label:\\"6 - Done but waiting to merge\\"" --json url --jq '.[].url'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        this.registerLibTester(new CheckDocumentationPullRequestsLibTester('3.0.0-beta1'))
        runScript('tests/jenkins/jobs/CheckDocumentationPRs_Jenkinsfile')
        def callStack = helper.getCallStack()
        assertCallStack().contains('checkDocumentationPullRequests.sh({script=gh pr list --repo opensearch-project/documentation-website --state open --label v3.0.0 -S "-label:\\"6 - Done but waiting to merge\\"" --json url --jq \'.[].url\', returnStdout=true})')
        assertCallStack().contains('No open pull requests found!')
    }
}
