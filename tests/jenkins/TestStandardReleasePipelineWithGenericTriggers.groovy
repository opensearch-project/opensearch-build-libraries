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



class TestStandardReleasePipelineWithGenericTriggers extends BuildPipelineTest {

    @Before
    void setUp() {
        helper.registerAllowedMethod("GenericTrigger", [Map.class], null)
        binding.setVariable('tag', '1.0.0')
        binding.setVariable('release_url', 'https://api.github.com/repos/Codertocat/Hello-World/releases/17372790')
        super.setUp()
    }


    @Test
    void testStandardReleasePipelineWithGenericTriggers() {
        super.testPipeline('tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile')
    }

    @Test
    void testStandardReleasePipelineWithTagTriggers() {
        super.testPipeline('tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggersTag_Jenkinsfile')
    }

    @Test
    void 'validate override values'() {
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile")
        def echoCommand = getCommands('echo').findAll{
            command -> command.contains('agent')
        }

        assertThat(echoCommand.size(), equalTo(1))
        assertThat(echoCommand, hasItem('Executing on agent [docker:[image:centos:7, reuseNode:false, stages:[:], args:, alwaysPull:true, containerPerStageRoot:false, label:AL2-X64]]'))
    }

    @Test
    void 'validate default triggers'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile")
        def cmd = getCommands('GenericTrigger').findAll{
            c -> c.contains('generic')
        }
        assertThat(cmd.size(), equalTo(1))
        assertThat(cmd, hasItem('{genericVariables=[{key=ref, value=$.release.tag_name}, {key=isDraft, value=$.release.draft}, {key=release_url, value=$.release.url}], tokenCredentialId=opensearch-ci-webhook-trigger-token, causeString=A tag was cut on opensearch-ci repo, printContributedVariables=false, printPostContent=false, regexpFilterText=$isDraft, regexpFilterExpression=true}'))
    }

    @Test
    void 'validate release is published'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile")
        def cmd = getCommands('sh').findAll{
            c -> c.contains('curl')
        }
        assertThat(cmd.size(), equalTo(1))
        assertThat(cmd, hasItem("curl -X PATCH -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer GIT_TOKEN' https://api.github.com/repos/Codertocat/Hello-World/releases/17372790 -d '{\"tag_name\":\"1.0.0\",\"draft\":false,\"prerelease\":false}'"))

    }

    @Test
    void 'use tag as trigger'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggersTag_Jenkinsfile")
        def cmd = getCommands('GenericTrigger').findAll{
            c -> c.contains('generic')
        }
        assertThat(cmd.size(), equalTo(1))
        assertThat(cmd, hasItem('{genericVariables=[{key=ref, value=.ref}, {key=isDraft, value=$.release.draft}, {key=release_url, value=$.release.url}], tokenCredentialId=opensearch-ci-webhook-trigger-token, causeString=A tag was cut on opensearch-ci repo, printContributedVariables=false, printPostContent=false, regexpFilterText=$ref, regexpFilterExpression=^refs/tags/.*}'))
    }

    @Test
    void 'validate release is not published'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggersTag_Jenkinsfile")
        def cmd = getCommands('sh').findAll{
            c -> c.contains('curl')
        }
        assertThat(cmd.size(), equalTo(0))
    }

    def getCommands(String method) {
        def echoCommands = helper.callStack.findAll { call ->
            call.methodName == method
        }.collect { call ->
            callArgsToString(call)
        }
        return echoCommands
    }
}
