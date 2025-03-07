
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
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestRunBenchmarkTestScriptWithEndpoint extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new RunBenchmarkTestEndpointLibTester(
                'execute-test',
                'opensearch-ABCxdfdfhyfk.com',
                'false',
                'nyc_taxis',
                'true',
                '',
                '',
                '',
                '',
                '',
                ''
        ))
        super.setUp()
    }

    @Test
    public void testRunBenchmarkTestScript_PipelineSingleNode() {
        super.testPipeline("tests/jenkins/jobs/BenchmarkTestWithEndpoint_Jenkinsfile")
    }

    @Test
    void testRunBenchmarkTestScript_verifyScriptExecutionsNoManifest() {
        runScript("tests/jenkins/jobs/BenchmarkTestWithEndpoint_Jenkinsfile")

        def testScriptCommands = getCommandExecutions('sh', './test.sh').findAll {
            shCommand -> shCommand.contains('./test.sh')
        }

        assertThat(testScriptCommands.size(), equalTo(1))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test execute-test    --cluster-endpoint opensearch-ABCxdfdfhyfk.com  --workload nyc_taxis --benchmark-config /tmp/workspace/benchmark.ini --user-tag true,security-enabled:true"
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
