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
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

class TestPublishToNpm extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
    }

    @Test
    void testWithTarball() {
        this.registerLibTester(new PublishToNpmLibTester('artifact', '/tmp/workspace/example.tgz'))
        super.testPipeline('tests/jenkins/jobs/PublishToNpmUsingTarball_JenkinsFile')
        assertThat(getShellCommands('npm'), hasItem(
            '\n            npm set registry \"https://registry.npmjs.org\"\n            npm set //registry.npmjs.org/:_authToken NPM_TOKEN\n            npm publish /tmp/workspace/example.tgz --dry-run && npm publish /tmp/workspace/example.tgz --access public --tag latest\n        '
        ))
        assertThat(getShellCommands('nvmrc'), hasItem('rm -rf /tmp/workspace/.nvmrc && rm -rf ~/.nvmrc'))
    }

    @Test
    void testWithRepoTag() {
        this.registerLibTester(new PublishToNpmLibTester('github'))
        super.testPipeline('tests/jenkins/jobs/PublishToNpm_Jenkinsfile')
        assertThat(getShellCommands('npm'), hasItem(
            '\n            npm set registry \"https://registry.npmjs.org\"\n            npm set //registry.npmjs.org/:_authToken NPM_TOKEN\n            npm publish  --dry-run && npm publish  --access public --tag beta\n        '
        ))
        assertThat(getShellCommands('nvmrc'), hasItem('rm -rf /tmp/workspace/.nvmrc && rm -rf ~/.nvmrc'))
    }

    def getShellCommands(searchtext) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == 'sh'
        }.collect { call ->
            callArgsToString(call)
        }.findAll { npmCommand ->
            npmCommand.contains(searchtext)
        }
        return shCommands
    }
}
