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

class TestRunPerfTestScript extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new RunPerfTestScriptLibTester(
            'tests/data/opensearch-1.3.0-bundle.yml',
            '1236',
            'true',
            'nyc_taxis',
            '1',
            '1',
            true
        ))
        super.setUp()
    }

    @Test
    public void testRunPerfTestScript_Pipeline() {
        super.testPipeline("tests/jenkins/jobs/PerfTest_Jenkinsfile")
    }

    @Test
    void testRunPerfTestScript_verifyArtifactDownloads() {
        runScript("tests/jenkins/jobs/PerfTest_Jenkinsfile")

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

        assertThat(s3DownloadCommands.size(), equalTo(2))
        assertThat(s3DownloadCommands, hasItem(
            "{file=config.yml, bucket=ARTIFACT_BUCKET_NAME, path=test_config/config.yml, force=true}".toString()
        ))
    }

    @Test
    void testRunPerfTestScript_verifyPackageInstallation() {
        runScript("tests/jenkins/jobs/PerfTest_Jenkinsfile")

        def pipenvCommands = getCommandExecutions('sh', 'pipenv').findAll {
            shCommand -> shCommand.contains('pipenv')
        }

        assertThat(pipenvCommands.size(), equalTo(2))

    }

    @Test
    void testRunPerfTestScript_verifyScriptExecutions() {
        runScript("tests/jenkins/jobs/PerfTest_Jenkinsfile")

        def testScriptCommands = getCommandExecutions('sh', './test.sh').findAll {
            shCommand -> shCommand.contains('./test.sh')
        }

        assertThat(testScriptCommands.size(), equalTo(2))
        assertThat(testScriptCommands, hasItem(
            "./test.sh perf-test --stack test-single-security-1236-x64-307 --bundle-manifest tests/data/opensearch-1.3.0-bundle.yml --config config.yml  --workload nyc_taxis --test-iters 1 --warmup-iters 1 ".toString()
        ))
        assertThat(testScriptCommands, hasItem(
            "./test.sh perf-test --stack test-single-1236-x64-307 --bundle-manifest tests/data/opensearch-1.3.0-bundle.yml --config config.yml --without-security --workload nyc_taxis --test-iters 1 --warmup-iters 1 ".toString()
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