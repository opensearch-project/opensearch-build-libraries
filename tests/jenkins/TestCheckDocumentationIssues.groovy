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
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckDocumentationIssues extends BuildPipelineTest {
    @Override
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('withCredentials', [Map])
        helper.addShMock("""gh issue list --repo opensearch-project/documentation-website --state open --label v3.0.0 -S "-linked:pr" --json number --jq '.[].number'""") { script ->
            return [stdout: "22\n23", exitValue: 0]
        }
        addParam('VERSION', '3.0.0')
        Random.metaClass.nextInt = { int max -> 2 }
    }

    @Test
    void testNotifyActionWithAssignee(){
        addParam('ACTION', 'notify')
        helper.addShMock("""gh issue view 22 --repo opensearch-project/documentation-website --json assignees --jq '.assignees[0].login'""") { script ->
            return [stdout: "foo", exitValue: 0]
        }
        this.registerLibTester(new CheckDocumentationIssuesLibTester('3.0.0', 'notify'))
        super.testPipeline('tests/jenkins/jobs/CheckDocumentationIssues_Jenkinsfile')
        assertThat(getCommands('sh', 'issue'), hasItem("{script=gh issue comment 22 --repo opensearch-project/documentation-website --body-file /tmp/workspace/CCCCCCCCCC.md, returnStdout=true}"))
        def fileContent = getCommands('writeFile', 'documentation')[0]
        assertThat(fileContent, allOf(
                containsString("{file=/tmp/workspace/CCCCCCCCCC.md, text=Hi @foo, </br>"),
                containsString("As part of the [entrance criteria](https://github.com/opensearch-project/.github/blob/main/RELEASING.md#entrance-criteria-to-start-release-window), all the documentation pull requests need to be drafted and in technical review. </br>"),
                containsString("**Since there is no pull request linked to this issue, please take one of the following actions:** </br>"),
                containsString("* Create the pull request and [link it](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue) to this issue. </br>"),
                containsString("* If you already have a pull request created, please [link it](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue) to this issue. </br>"),
                containsString("* If this feature is not targeted for the currently labeled release version, please update the issue with the correct release version. </br>"),
                containsString("Please note: Missing documentation can block the release and cause delays in the overall process. </br>")
        ))
    }
    @Test
    void testCheckAction(){
        addParam('ACTION', 'check')
        this.registerLibTester(new CheckDocumentationIssuesLibTester('3.0.0', 'check'))
        runScript('tests/jenkins/jobs/CheckDocumentationIssues_Jenkinsfile')
        assertThat(getCommands('echo', 'documentation'), hasItem("Open documentation issues found. Issue numbers: [22, 23]"))
    }

    @Test
    void testCheckActionWithQualifier(){
        addParam('VERSION', '3.0.0-alpha1')
        addParam('ACTION', 'check')
        this.registerLibTester(new CheckDocumentationIssuesLibTester('3.0.0', 'check'))
        runScript('tests/jenkins/jobs/CheckDocumentationIssues_Jenkinsfile')
        assertThat(getCommands('echo', 'documentation'), hasItem("Open documentation issues found. Issue numbers: [22, 23]"))
    }

    @Test
    void testCheckActionWithNoOpenIssues(){
        addParam('ACTION', 'check')
        helper.addShMock("""gh issue list --repo opensearch-project/documentation-website --state open --label v3.0.0 -S "-linked:pr" --json number --jq '.[].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        this.registerLibTester(new CheckDocumentationIssuesLibTester('3.0.0', 'check'))
        runScript('tests/jenkins/jobs/CheckDocumentationIssues_Jenkinsfile')
        assertThat(getCommands('echo', 'documentation'), not(hasItem("Open documentation issues found. Issue numbers: [22, 23]")))
        assertThat(getCommands('echo', 'documentation'), hasItem("No open documentation issues found without a linked PR!"))
    }

    @Test
    void testNotifyActionWithAuthor(){
        addParam('ACTION', 'notify')
        helper.addShMock("""gh issue view 22 --repo opensearch-project/documentation-website --json assignees --jq '.assignees[0].login'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue view 22 --repo opensearch-project/documentation-website --json author --jq '.author.login'""") { script ->
            return [stdout: "bar", exitValue: 0]
        }
        this.registerLibTester(new CheckDocumentationIssuesLibTester('3.0.0', 'notify'))
        runScript('tests/jenkins/jobs/CheckDocumentationIssues_Jenkinsfile')
        assertThat(getCommands('sh', 'issue'), hasItem("{script=gh issue comment 22 --repo opensearch-project/documentation-website --body-file /tmp/workspace/CCCCCCCCCC.md, returnStdout=true}"))
        def fileContent = getCommands('writeFile', 'documentation')[0]
        assertThat(fileContent, containsString("{file=/tmp/workspace/CCCCCCCCCC.md, text=Hi @bar, </br>"))
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
