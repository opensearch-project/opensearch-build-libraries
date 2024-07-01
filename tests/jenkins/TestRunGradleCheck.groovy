/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.tests.BuildPipelineTest
import org.junit.Before
import org.junit.Test

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat


class TestRunGradleCheck extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new RunGradleCheckLibTester(
            'https://github.com/opensearch-project/OpenSearch',
            'main',
            'false'
            )
        )
        super.setUp()
    }

    @Test
    void testRunGradleCheck() {
        super.testPipeline("tests/jenkins/jobs/RunGradleCheck_Jenkinsfile")

    }

    @Test
    void testNoAlignParamInGradleCommand() {
        runScript("tests/jenkins/jobs/RunGradleCheck_Jenkinsfile")

        def gradleCommands = getCommandExecutions('sh', 'gradle').findAll {
            shCommand -> shCommand.contains('gradle')
        }
        assertThat(gradleCommands, hasItem(containsString("./gradlew clean && ./gradlew check -Dtests.coverage=true  --no-daemon --no-scan || GRADLE_CHECK_STATUS=1")
        ))
    }

    @Test
    void testRunGradleCheckBwcAlign() {
        super.testPipeline("tests/jenkins/jobs/RunGradleCheckBwcAlign_Jenkinsfile")
    }

    @Test
    void testAlignParamInGradleCommand() {
        runScript("tests/jenkins/jobs/RunGradleCheckBwcAlign_Jenkinsfile")

        def gradleCommands = getCommandExecutions('sh', 'gradle').findAll {
            shCommand -> shCommand.contains('gradle')
        }
        assertThat(gradleCommands, hasItem(containsString("./gradlew clean && ./gradlew check -Dtests.coverage=true -Dbwc.checkout.align=true --no-daemon --no-scan || GRADLE_CHECK_STATUS=1")
        ))
    }

    def getCommandExecutions(methodName, command) {
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
