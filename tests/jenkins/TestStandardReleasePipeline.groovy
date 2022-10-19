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
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.CoreMatchers.equalTo
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem



class TestStandardReleasePipeline extends BuildPipelineTest {

    @Before
    void setUp() {
        super.setUp()
    }

    @Test
    void testStandardReleasePipeline() {
        super.testPipeline("tests/jenkins/jobs/StandardReleasePipeline_Jenkinsfile")
    }

    @Test
    void testStandardReleasePipelineWithArgs() {
        super.testPipeline("tests/jenkins/jobs/StandardReleasePipelineWithArgs_JenkinsFile")
    }

    @Test
    void 'check overide values'() {
        runScript('tests/jenkins/jobs/StandardReleasePipelineWithArgs_JenkinsFile')
        def echoCommand = getEchoCommands().findAll{
            command -> command.contains('agent')
        }

        assertThat(echoCommand.size(), equalTo(1))
        assertThat(echoCommand, hasItem('Executing on agent [docker:[image:test:image, reuseNode:false, stages:[:], args:, alwaysPull:true, containerPerStageRoot:false, label:AL2-X64]]'))
    }

    @Test 
    void 'check default values'(){
        runScript('tests/jenkins/jobs/StandardReleasePipeline_Jenkinsfile')
        def echoCommand = getEchoCommands().findAll{
            command -> command.contains('agent')
        }

        assertThat(echoCommand.size(), equalTo(1))
        assertThat(echoCommand, hasItem('Executing on agent [docker:[image:opensearchstaging/ci-runner:ci-runner-centos7-opensearch-build-v2, reuseNode:false, stages:[:], args:, alwaysPull:true, containerPerStageRoot:false, label:Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host]]'))
    }

    def getEchoCommands() {
        def echoCommands = helper.callStack.findAll { call ->
            call.methodName == 'echo'
        }.collect { call ->
            callArgsToString(call)
        }
        return echoCommands
    }
}
