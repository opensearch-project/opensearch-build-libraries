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
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Test

import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.MatcherAssert.assertTrue

class TestGetCompareBenchmarkIds extends BuildPipelineTest {
    @Before
    void setUp() {
        this.registerLibTester(new GetCompareBenchmarkIdsLibTester(
                'test-baseline-config',
                '3.0.0',
                'big5',
                '12345'))
        super.setUp()
    }

    @Test
    public void testGetCompareBenchmarkIds_default() {
        super.testPipeline("tests/jenkins/jobs/CompareBenchmarkRun_Jenkinsfile")
    }

    @Test
    void testCallCurlCommands() {
        runScript("tests/jenkins/jobs/CompareBenchmarkRun_Jenkinsfile")

        def curlCommands = getCommandExecutions('sh', 'curl').findAll {
            shCommand -> shCommand.contains('curl')
        }
        assertThat(curlCommands.size(), equalTo(2))
        def baselineCurlCommand = curlCommands[0]
        assertThat(baselineCurlCommand, containsString('"user-tags.cluster-config": "test-baseline-config"'))
        assertThat(baselineCurlCommand, containsString('"workload": "big5"'))
        assertThat(baselineCurlCommand, containsString('"distribution-version": "3.0.0"'))

        def contenderCurlCommand = curlCommands[1]
        assertThat(contenderCurlCommand, containsString('"user-tags.pull_request_number": "12345"'))
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
