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
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestRunBenchmarkTestScriptMultiNode extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new RunBenchmarkTestScriptLibTester(
                'execute-test',
                'tests/data/opensearch-1.3.0-bundle.yml',
                '',
                '',
                'true',
                'nyc_taxis',
                'false',
                'false',
                'true',
                'true',
                '3',
                '3',
                '',
                'key1:value1',
                '{"key2":"value2"}',
                'custom-test-procedure',
                'index-append,default',
                'type:search,index',
                'cluster.indices.replication.strategy:SEGMENT',
                'true',
                'false',
                '{"telemetry_setting":"value"}',
                'false'
        ))
        super.setUp()
    }

    @Test
    public void testRunBenchmarkTestScript_PipelineMultiNode() {
        super.testPipeline("tests/jenkins/jobs/BenchmarkTestMultinode_Jenkinsfile")
    }


    @Test
    void testRunBenchmarkTestScript_verifyScriptExecutionsMultiNode() {
        runScript("tests/jenkins/jobs/BenchmarkTestMultinode_Jenkinsfile")

        def testScriptCommands = getCommandExecutions('sh', './test.sh').findAll {
            shCommand -> shCommand.contains('./test.sh')
        }

        assertThat(testScriptCommands.size(), equalTo(2))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test execute-test --bundle-manifest tests/data/opensearch-1.3.0-bundle.yml    --config /tmp/workspace/config.yml --workload nyc_taxis --benchmark-config /tmp/workspace/benchmark.ini --user-tag distribution-build-id:1236,arch:x64,os-commit-id:22408088f002a4fc8cdd3b2ed7438866c14c5069,key1:value1,security-enabled:true      --use-50-percent-heap --enable-remote-store --capture-node-stat   --suffix 307-secure --manager-node-count 3 --data-node-count 3     --workload-params '{\"key2\":\"value2\"}' --test-procedure custom-test-procedure --exclude-tasks index-append,default --include-tasks type:search,index --additional-config cluster.indices.replication.strategy:SEGMENT --data-node-storage 200 --ml-node-storage 200  --telemetry-params '{\"telemetry_setting\":\"value\"}'".toString()
        ))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test execute-test --bundle-manifest tests/data/opensearch-1.3.0-bundle.yml    --config /tmp/workspace/config.yml --workload nyc_taxis --benchmark-config /tmp/workspace/benchmark.ini --user-tag distribution-build-id:1236,arch:x64,os-commit-id:22408088f002a4fc8cdd3b2ed7438866c14c5069,key1:value1,security-enabled:false --without-security     --use-50-percent-heap --enable-remote-store --capture-node-stat   --suffix 307 --manager-node-count 3 --data-node-count 3     --workload-params '{\"key2\":\"value2\"}' --test-procedure custom-test-procedure --exclude-tasks index-append,default --include-tasks type:search,index --additional-config cluster.indices.replication.strategy:SEGMENT --data-node-storage 200 --ml-node-storage 200  --telemetry-params '{\"telemetry_setting\":\"value\"}'".toString()
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
