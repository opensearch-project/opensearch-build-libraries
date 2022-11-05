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

        this.registerLibTester(new PublishToNpmLibTester('https://github.com/opensearch-project/opensearch-ci', '1.0.0'))
        super.setUp()
    }

    @Test
    public void test() {
        super.testPipeline("tests/jenkins/jobs/PublishToNpm_Jenkinsfile")
    }

    @Test
    void 'verify shell commands'(){
        runScript('tests/jenkins/jobs/PublishToNpm_Jenkinsfile')

        def npmCommands = getShellCommands()
        assertThat(npmCommands, hasItem(
            'npm set registry "https://registry.npmjs.org"; npm set //registry.npmjs.org/:_authToken NPM_TOKEN; npm publish --dry-run && npm publish --access public'.toString()
        ))

    }
    def getShellCommands() {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == 'sh'
        }.collect { call ->
            callArgsToString(call)
        }.findAll { npmCommand ->
            npmCommand.contains('npm')
        }
        return shCommands
    }
}
