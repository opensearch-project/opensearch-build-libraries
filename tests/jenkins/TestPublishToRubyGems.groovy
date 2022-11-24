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

class TestPublishToRubyGems extends BuildPipelineTest {

    @Test
    void testPublishingRuby() {
        this.registerLibTester(new PublishToRubyGemsLibTester('ruby-api-key'))
        super.setUp()
        super.testPipeline('tests/jenkins/jobs/PublishToRubyGems_JenkinsFile')
        def curlCommands = getCommands('sh', 'curl')
        assertThat(curlCommands, hasItem(
            "cd /tmp/workspace/dist && curl --fail --data-binary @`ls *.gem` -H 'Authorization:API_KEY' -H 'Content-Type: application/octet-stream' https://rubygems.org/api/v1/gems"
        ))
        assertThat(curlCommands, hasItem(
            "cd /tmp/workspace/test && curl --fail --data-binary @`ls *.gem` -H 'Authorization:API_KEY' -H 'Content-Type: application/octet-stream' https://rubygems.org/api/v1/gems"
        ))
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
