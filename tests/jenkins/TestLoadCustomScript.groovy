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
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestLoadCustomScript extends BuildPipelineTest {
    @Test
    void testLoadCustomScript(){
        this.registerLibTester(new LoadCustomScriptLibTester('test-scripts/hello-world.sh', 'hello-world.sh'))
        super.setUp()
        super.testPipeline("tests/jenkins/jobs/LoadCustomScript_Jenkinsfile")

        def loadScript = getCommands('loadCustomScript', '')
        assertThat(loadScript, hasItem('{scriptPath=test-scripts/hello-world.sh, scriptName=hello-world.sh}'))
    }

    def getCommands(method, text) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == method
        }.collect { call ->
            callArgsToString(call)
        }.findAll { command ->
            command.contains(text)
        }
        return shCommands
    }
}
