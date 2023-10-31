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


class TestCreateGithubIssue extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
    }

    @Test
    void testCreateGithubIssueComment() {
        this.registerLibTester(new CreateGithubIssueLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "Test GH issue body",
            "label101"
        ))
        helper.addShMock("date -d \"5 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title is:closed closed:>=2023-10-24" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem("Issue already exists, adding a comment"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment 22 --repo https://github.com/opensearch-project/opensearch-build --body \"Test GH issue body\", returnStdout=true}"))
    }

    void testCreateGithubIssueCreate() {
        this.registerLibTester(new CreateGithubIssueLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "Test GH issue body",
            "label101"
        ))
        helper.addShMock("date -d \"5 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title is:closed closed:>=2023-10-24" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem("Creating new issue"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue create --title \"Test GH issue title\" --body \"Test GH issue body\" --label \"label101\" --label \"untriaged\" --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}"))
    }

    void testCreateGithubIssueReOpen() {
        this.registerLibTester(new CreateGithubIssueLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "Test GH issue body",
            "label101"
        ))
        helper.addShMock("date -d \"5 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title is:closed closed:>=2023-10-24" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem("Re-opening a recently closed issue and commenting on it"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue reopen --repo https://github.com/opensearch-project/opensearch-build 22, returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment 22 --repo https://github.com/opensearch-project/opensearch-build  --body \"Test GH issue body\", returnStdout=true}"))
    }

    void testCreateGithubIssueReOpenWithDays() {
        this.registerLibTester(new CreateGithubIssueLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "Test GH issue body",
            "label101",
            "5"
        ))
        super.testPipeline('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'script'), hasItem("""{script=date -d "5 days ago" +'%Y-%m-%d'}"""))
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

