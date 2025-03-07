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
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.CoreMatchers.equalTo
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import groovy.json.JsonSlurper



class TestStandardReleasePipelineWithGenericTriggers extends BuildPipelineTest {

    @Before
    void setUp() {
        def json = '''[{
            "url": "https://api.github.com/repos/owner/reponame/releases/assets/123456",
            "id": 123456,
            "node_id": "RA_kwDOIZCTQs4FAna6",
            "name": "artifacts.tar.gz",
            "label": "",
            "uploader": {
            "login": "github-actions[bot]",
            "id": 41898282,
            "node_id": "MDM6Qm90NDE4OTgyODI=",
            "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
            "gravatar_id": "",
            "url": "https://api.github.com/users/github-actions%5Bbot%5D",
            "html_url": "https://github.com/apps/github-actions",
            "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
            "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
            "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
            "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
            "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
            "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
            "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
            "type": "Bot",
            "site_admin": false
            },
            "content_type": "application/gzip",
            "state": "uploaded",
            "size": 203429,
            "download_count": 0,
            "created_at": "2022-11-09T19:57:17Z",
            "updated_at": "2022-11-09T19:57:17Z",
            "browser_download_url": "https://github.com/owner/reponame/releases/download/untagged-959f2cde363466e20879/artifacts.tar.gz"
        }]'''
        helper.registerAllowedMethod("GenericTrigger", [Map.class], null)
        binding.setVariable('tag', '1.0.0')
        binding.setVariable('release_url', 'https://api.github.com/repos/Codertocat/Hello-World/releases/17372790')
        binding.setVariable('assets_url', 'https://api.github.com/repos/owner/name/releases/1234/assets')
        helper.registerAllowedMethod('readJSON', [Map], { Map parameters ->
            return new JsonSlurper().parseText(json)
        })
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
        assertThat(echoCommand, hasItem('Executing on agent [docker:[image:centos:7, reuseNode:false, stages:[:], args:-e JAVA_HOME=/opt/java/openjdk-17, alwaysPull:true, containerPerStageRoot:false, label:AL2-X64]]'))
    }

    @Test
    void 'validate default triggers'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile")
        def cmd = getCommands('GenericTrigger').findAll{
            c -> c.contains('generic')
        }
        assertThat(cmd.size(), equalTo(1))
        assertThat(cmd, hasItem('{genericVariables=[{key=ref, value=$.release.tag_name}, {key=repository, value=$.repository.html_url}, {key=action, value=$.action}, {key=isDraft, value=$.release.draft}, {key=release_url, value=$.release.url}, {key=assets_url, value=$.release.assets_url}], tokenCredentialId=opensearch-ci-webhook-trigger-token, causeString=A tag was cut on opensearch-ci repo, printContributedVariables=false, printPostContent=false, regexpFilterText=$isDraft $action, regexpFilterExpression=^true created$}'))
    }

    @Test
    void 'validate release is published'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile")
        def cmd = getCommands('sh').findAll{
            c -> c.contains('curl')
        }
        assertThat(cmd, hasItem("curl -X PATCH -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer GITHUB_TOKEN' https://api.github.com/repos/Codertocat/Hello-World/releases/17372790 -d '{\"tag_name\":\"1.0.0\",\"draft\":false,\"prerelease\":false}'"))

    }

   @Test
   void 'verify download assets'() {
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile")
        def cmd = getCommands('sh').findAll{
            c -> c.contains('curl')
        }
        assertThat(cmd, hasItem("curl -J -L -H 'Accept: application/octet-stream' -H 'Authorization: Bearer GITHUB_TOKEN' https://api.github.com/repos/owner/reponame/releases/assets/123456 -o artifacts.tar.gz && tar -xvf artifacts.tar.gz"))
        assertThat(cmd, hasItem("{script=curl -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer GITHUB_TOKEN' https://api.github.com/repos/owner/name/releases/1234/assets, returnStdout=true}"))
    }

    @Test
    void 'validate default values'() {
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggersTag_Jenkinsfile")
        def echoCommand = getCommands('echo').findAll{
            command -> command.contains('agent')
        }

        assertThat(echoCommand.size(), equalTo(1))
        assertThat(echoCommand, hasItem('Executing on agent [docker:[image:opensearchstaging/ci-runner:release-centos7-clients-v4, reuseNode:false, stages:[:], args:-e JAVA_HOME=/opt/java/openjdk-11, alwaysPull:true, containerPerStageRoot:false, label:Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host]]'))
    }

    @Test
    void 'use tag as trigger'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggersTag_Jenkinsfile")
        def cmd = getCommands('GenericTrigger').findAll{
            c -> c.contains('generic')
        }
        assertThat(cmd.size(), equalTo(1))
        assertThat(cmd, hasItem('{genericVariables=[{key=ref, value=.ref}, {key=repository, value=$.repository.html_url}, {key=action, value=$.action}, {key=isDraft, value=$.release.draft}, {key=release_url, value=$.release.url}, {key=assets_url, value=$.release.assets_url}], tokenCredentialId=opensearch-ci-webhook-trigger-token, causeString=A tag was cut on opensearch-ci repo, printContributedVariables=false, printPostContent=false, regexpFilterText=$ref, regexpFilterExpression=^refs/tags/.*}'))
    }

    @Test
    void 'validate skipping download stage'(){
        runScript("tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggersTag_Jenkinsfile")
        def cmd = getCommands('echo').findAll{
            c -> c.contains('stage')
        }
        assertThat(cmd, hasItem('Skipping stage Download artifacts'))
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
