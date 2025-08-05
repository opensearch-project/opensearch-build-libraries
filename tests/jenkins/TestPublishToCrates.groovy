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

class TestPublishToCrates extends BuildPipelineTest {

    @Test
    void testWithoutPackage() {
        this.registerLibTester(new PublishToCratesLibTester('https://github.com/opensearch-project/opensearch-rs', '1.0.0'))
        super.setUp()
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('API_TOKEN', "API_TOKEN")
        super.testPipeline('tests/jenkins/jobs/PublishToCrates_Jenkinsfile')
        assertThat(getCommands('sh', 'cargo'), hasItem('cargo publish  --dry-run && cargo publish  --token API_TOKEN'))
    }

    @Test
    void testWithPackage() {
        this.registerLibTester(new PublishToCratesLibTester('https://github.com/opensearch-project/opensearch-rs', '1.0.0', 'opensearch'))
        super.setUp()
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('API_TOKEN', "API_TOKEN")
        super.testPipeline('tests/jenkins/jobs/publishToCratesWithPackage_Jenkinsfile')
        assertThat(getCommands('sh', 'cargo'), hasItem('cargo publish -p opensearch --dry-run && cargo publish -p opensearch --token API_TOKEN'))
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
