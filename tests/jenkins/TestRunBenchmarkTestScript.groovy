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

class TestRunBenchmarkTestScript extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new RunBenchmarkTestScriptLibTester(
                'execute-test',
                'tests/data/opensearch-1.3.0-bundle.yml',
                '',
                '',
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
                '{"key2":"value2"}',
                '',
                '',
                '',
                'cluster.indices.replication.strategy:SEGMENT',
                'false',
                'true',
                '',
                'false'
        ))
        super.setUp()
    }

    @Test
    public void testRunBenchmarkTestScript_PipelineSingleNode() {
        super.testPipeline("tests/jenkins/jobs/BenchmarkTest_Jenkinsfile")
    }

    @Test
    void testRunBenchmarkTestScript_verifyArtifactDownloads() {
        runScript("tests/jenkins/jobs/BenchmarkTest_Jenkinsfile")

        def curlCommands = getCommandExecutions('sh', 'curl').findAll {
            shCommand -> shCommand.contains('curl')
        }

        assertThat(curlCommands.size(), equalTo(2))
        assertThat(curlCommands, hasItem(
                "curl -sSL --retry 5 test://artifact.url --output tests/data/opensearch-1.3.0-bundle.yml".toString()
        ))

        def s3DownloadCommands = getCommandExecutions('s3Download', 'bucket').findAll {
            shCommand -> shCommand.contains('bucket')
        }

        assertThat(s3DownloadCommands.size(), equalTo(4))
        assertThat(s3DownloadCommands, hasItem(
                "{file=config.yml, bucket=ARTIFACT_BUCKET_NAME, path=test_config/config.yml, force=true}".toString()
        ))
        assertThat(s3DownloadCommands, hasItem(
                "{file=benchmark.ini, bucket=ARTIFACT_BUCKET_NAME, path=test_config/benchmark.ini, force=true}".toString()
        ))
    }

    @Test
    void testRunBenchmarkTestScript_verifyScriptExecutionsSingleNode() {
        runScript("tests/jenkins/jobs/BenchmarkTest_Jenkinsfile")

        def testScriptCommands = getCommandExecutions('sh', './test.sh').findAll {
            shCommand -> shCommand.contains('./test.sh')
        }

        assertThat(testScriptCommands.size(), equalTo(2))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test execute-test --bundle-manifest tests/data/opensearch-1.3.0-bundle.yml    --config /tmp/workspace/config.yml --workload nyc_taxis --benchmark-config /tmp/workspace/benchmark.ini --user-tag distribution-build-id:1236,arch:x64,os-commit-id:22408088f002a4fc8cdd3b2ed7438866c14c5069,security-enabled:true    --single-node  --use-50-percent-heap    --capture-segment-replication-stat --suffix 307-secure      --data-instance-type r5.8xlarge --workload-params '{\"key2\":\"value2\"}'    --additional-config cluster.indices.replication.strategy:SEGMENT --data-node-storage 200 --ml-node-storage 200"
        ))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test execute-test --bundle-manifest tests/data/opensearch-1.3.0-bundle.yml    --config /tmp/workspace/config.yml --workload nyc_taxis --benchmark-config /tmp/workspace/benchmark.ini --user-tag distribution-build-id:1236,arch:x64,os-commit-id:22408088f002a4fc8cdd3b2ed7438866c14c5069,security-enabled:false --without-security   --single-node  --use-50-percent-heap    --capture-segment-replication-stat --suffix 307      --data-instance-type r5.8xlarge --workload-params '{\"key2\":\"value2\"}'    --additional-config cluster.indices.replication.strategy:SEGMENT --data-node-storage 200 --ml-node-storage 200"
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
