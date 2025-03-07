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
        assertThat(getCommands('sh', 'script'), hasItem("""{script=gh issue comment bbb\nccc --repo https://github.com/opensearch-project/opensearch-build --body \"Test GH issue body\", returnStdout=true}"""))
    }

    @Test
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
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title is:closed closed:>=2023-10-24" --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem("Creating new issue"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue create --title \"Test GH issue title\" --body \"Test GH issue body\" --label \"label101\" --label \"untriaged\" --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}"))
    }

    @Test
    void testCreateGithubIssueCreateWithMissingLabel() {
        this.registerLibTester(new CreateGithubIssueLibTester(
                "https://github.com/opensearch-project/opensearch-build",
                "Test GH issue title",
                "Test GH issue body",
                "label101"
        ))
        helper.addShMock("date -d \"5 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title is:closed closed:>=2023-10-24" --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh label list --repo https://github.com/opensearch-project/opensearch-build -S "label101" --json name --jq '.[0].name'""") { script ->
            return [stdout: "no labels in opensearch-project/opensearch-build matched your search", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem("Creating new issue"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh label create label101 --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue create --title \"Test GH issue title\" --body \"Test GH issue body\" --label \"label101\" --label \"untriaged\" --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}"))
    }

    @Test
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
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title is:closed closed:>=2023-10-24" --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem("Re-opening a recently closed issue and commenting on it"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue reopen --repo https://github.com/opensearch-project/opensearch-build 22, returnStdout=true}"))
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh issue comment 22 --repo https://github.com/opensearch-project/opensearch-build --body \"Test GH issue body\", returnStdout=true}"))
    }

    @Test
    void testCreateGithubIssueReOpenWithDays() {
        this.registerLibTester(new CreateGithubIssueLibTester(
                "https://github.com/opensearch-project/opensearch-build",
                "Test GH issue title",
                "Test GH issue body",
                "label101",
                "5"
        ))
        runScript('tests/jenkins/jobs/CreateGithubIssue_Jenkinsfile')
        assertThat(getCommands('sh', 'script'), hasItem("{script=date -d \"5 days ago\" +'%Y-%m-%d', returnStdout=true}"))
    }

    @Test
    void testCreateGithubIssueWithBodyFile() {
        helper.addShMock("date -d \"5 days ago\" +'%Y-%m-%d'") { script ->
            return [stdout: "2023-10-24", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "22", exitValue: 0]
        }
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title is:closed closed:>=2023-10-24" --label label101 --json number --jq '.[0].number'""") { script ->
            return [stdout: "", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/EditGithubIssue_Jenkinsfile', 'tests/jenkins/jobs/EditGithubIssue_Jenkinsfile_IssueBody')
        assertThat(getCommands('println', ''), hasItem("Issue already exists, editing the issue body"))
        assertThat(getCommands('sh', 'script'), hasItem("""{script=gh issue edit bbb\nccc --repo https://github.com/opensearch-project/opensearch-build --body-file issueBody.md, returnStdout=true}"""))
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
