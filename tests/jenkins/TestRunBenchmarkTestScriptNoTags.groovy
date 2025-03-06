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

class TestRunBenchmarkTestScriptNoManifest extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new RunBenchmarkTestScriptLibTester(
                'execute-test',
                '',
                'https://www.example.com/example.tar.gz',
                '3.0.0',
                'true',
                'nyc_taxis',
                'true',
                'false',
                'true',
                'false',
                '',
                '',
                'r5.8xlarge',
                '',
                '',
                'custom-test-procedure',
                '',
                '',
                'cluster.indices.replication.strategy:SEGMENT',
                'false',
                'true',
                '',
                'true'
        ))
        super.setUp()
    }

    @Test
    public void testRunBenchmarkTestScript_PipelineSingleNode() {
        super.testPipeline("tests/jenkins/jobs/BenchmarkTestNoManifest_Jenkinsfile")
    }

    @Test
    void testRunBenchmarkTestScript_verifyScriptExecutionsNoManifest() {
        runScript("tests/jenkins/jobs/BenchmarkTestNoManifest_Jenkinsfile")

        def testScriptCommands = getCommandExecutions('sh', './test.sh').findAll {
            shCommand -> shCommand.contains('./test.sh')
        }

        assertThat(testScriptCommands.size(), equalTo(2))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test execute-test  --distribution-url https://www.example.com/example.tar.gz --distribution-version 3.0.0  --config /tmp/workspace/config.yml --workload nyc_taxis --benchmark-config /tmp/workspace/benchmark.ini --user-tag security-enabled:true    --single-node  --use-50-percent-heap   --enable-instance-storage --capture-segment-replication-stat --suffix 307-secure      --data-instance-type r5.8xlarge  --test-procedure custom-test-procedure   --additional-config cluster.indices.replication.strategy:SEGMENT --data-node-storage 200 --ml-node-storage 200"
        ))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test execute-test  --distribution-url https://www.example.com/example.tar.gz --distribution-version 3.0.0  --config /tmp/workspace/config.yml --workload nyc_taxis --benchmark-config /tmp/workspace/benchmark.ini --user-tag security-enabled:false --without-security   --single-node  --use-50-percent-heap   --enable-instance-storage --capture-segment-replication-stat --suffix 307      --data-instance-type r5.8xlarge  --test-procedure custom-test-procedure   --additional-config cluster.indices.replication.strategy:SEGMENT --data-node-storage 200 --ml-node-storage 200"
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
