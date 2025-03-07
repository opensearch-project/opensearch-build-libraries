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


class TestCreateReleaseTag extends BuildPipelineTest {

    @Before
    void setUp() {

        this.registerLibTester(new CreateReleaseTagLibTester('tests/data/opensearch-build-1.1.0.yml', '1.1.0'))
        super.setUp()
        super.testPipeline("tests/jenkins/jobs/CreateReleaseTag_Jenkinsfile")
    }

    @Test
    void testCreateReleaseTag_verifyGitCommands() {
        def gitCheckoutCommands = getCommandExecutions('checkout', 'GitSCM').findAll {
            shCommand -> shCommand.contains('git')
         }
         def gitshellCommands = getCommandExecutions('sh', 'git').findAll {
            shCommand -> shCommand.contains('git')
         }
         assertThat(gitCheckoutCommands, hasItem("{\$class=GitSCM, branches=[{name=3913d7097934cbfe1fdcf919347f22a597d00b76}], userRemoteConfigs=[{url=https://github.com/opensearch-project/common-utils.git}]}".toString()))
         assertThat(gitshellCommands, hasItem("git tag 1.1.0".toString()))
         assertThat(gitshellCommands, hasItem("git push https://GITHUB_TOKEN@github.com/opensearch-project/OpenSearch.git 1.1.0".toString()))

    }

    def getCommandExecutions(methodName, command=null) {
    def shCommands = helper.callStack.findAll {
        call ->
            call.methodName == methodName
    }.
    collect {
        call ->
            callArgsToString(call)
    }.findAll {
        shCommand ->
            shCommand.contains(command)
    }

    return shCommands
}
}
