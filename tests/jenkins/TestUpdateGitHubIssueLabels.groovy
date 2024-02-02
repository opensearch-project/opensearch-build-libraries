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
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.jupiter.api.Assertions.assertThrows


class TestUpdateGitHubIssueLabels extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
    }

    @Test
    void testIssueDoesNotExist() {
        this.registerLibTester(new updateGitHubIssueLabelsLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "label101,label102",
            "add"
        ))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: " ", exitValue: 0]
        }
        super.testPipeline('tests/jenkins/jobs/UpdateGitHubIssueLabels_Jenkinsfile')
        assertThat(getCommands('println', ''), hasItem('No open issues found for https://github.com/opensearch-project/opensearch-build'))
    }

    @Test
    void testLabelCreation() {
        this.registerLibTester(new updateGitHubIssueLabelsLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "label101,label102",
            "add"
        ))
        helper.addShMock("""gh label list --repo https://github.com/opensearch-project/opensearch-build -S "label101" --json name --jq '.[0].name'""") { script ->
            return [stdout: "no labels in opensearch-project/opensearch-build matched your search", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateGitHubIssueLabels_Jenkinsfile')
        assertThat(getCommands('sh', 'script'), hasItem("{script=gh label create label101 --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}"))
    }

    @Test
    void testExistingLabelsAddition() {
        this.registerLibTester(new updateGitHubIssueLabelsLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "label101,label102",
            "add"
        ))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: "67", exitValue: 0]
        }
        helper.addShMock("""gh label list --repo https://github.com/opensearch-project/opensearch-build -S label101 --json name --jq '.[0].name'""") { script ->
            return [stdout: "label101", exitValue: 0]
        }
        helper.addShMock("""gh label list --repo https://github.com/opensearch-project/opensearch-build -S label102 --json name --jq '.[0].name'""") { script ->
            return [stdout: "label102", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateGitHubIssueLabels_Jenkinsfile')
        assertThat(getCommands('sh', 'script'), not(hasItem('{script=gh label create label101 --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}')))
        assertThat(getCommands('sh', 'script'), not(hasItem('{script=gh label create label102 --repo https://github.com/opensearch-project/opensearch-build, returnStdout=true}')))
        assertThat(getCommands('sh', 'script'), hasItems('{script=gh issue edit 67 -R https://github.com/opensearch-project/opensearch-build --add-label \"label101\", returnStdout=true}', '{script=gh issue edit 67 -R https://github.com/opensearch-project/opensearch-build --add-label \"label102\", returnStdout=true}'))
    }

    @Test
    void testSkippingLabelCreationForRemove() {
        this.registerLibTester(new updateGitHubIssueLabelsLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "label101",
            "remove"
        ))
        helper.addShMock("""gh label list --repo https://github.com/opensearch-project/opensearch-build -S "label101" --json name --jq '.[0].name'""") { script ->
            return [stdout: "no labels in opensearch-project/opensearch-build matched your search", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateGitHubIssueLabels_Removal_Jenkinsfile')
        assertThat(getCommands('println', 'label'), hasItem('label101 label does not exist. Skipping the label removal'))
    }

    @Test
    void testLabelRemoval() {
        this.registerLibTester(new updateGitHubIssueLabelsLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "label101",
            "remove"
        ))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: "67", exitValue: 0]
        }
        helper.addShMock("""gh label list --repo https://github.com/opensearch-project/opensearch-build -S label101 --json name --jq '.[0].name'""") { script ->
            return [stdout: "label101", exitValue: 0]
        }
        runScript('tests/jenkins/jobs/UpdateGitHubIssueLabels_Removal_Jenkinsfile')
        assertThat(getCommands('sh', 'script'), hasItem('{script=gh issue edit 67 -R https://github.com/opensearch-project/opensearch-build --remove-label \"label101\", returnStdout=true}'))
    }

    @Test
    void testFailure(){
        this.registerLibTester(new updateGitHubIssueLabelsLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "label101,label102",
            "add"
        ))
        helper.addShMock("""gh issue list --repo https://github.com/opensearch-project/opensearch-build -S "Test GH issue title in:title" --json number --jq '.[0].number'""") { script ->
            return [stdout: "Wrong credentials", exitValue: 127]
        }
        assertThrows(Exception) {
            runScript('tests/jenkins/jobs/UpdateGitHubIssueLabels_Jenkinsfile')
        }
        assertThat(getCommands('error', ''), hasItem('Unable to edit GitHub issue for https://github.com/opensearch-project/opensearch-build, Script returned error code: 127'))
    }
    @Test
    void testAction(){
        this.registerLibTester(new updateGitHubIssueLabelsLibTester(
            "https://github.com/opensearch-project/opensearch-build",
            "Test GH issue title",
            "label101",
            "delete"
        ))
        runScript('tests/jenkins/jobs/UpdateGitHubIssueLabels_Delete_Jenkinsfile')
        assertThat(getCommands('error', ''), hasItem("Invalid action 'delete' specified. Valid values: add, remove"))
        assertJobStatusFailure()
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

