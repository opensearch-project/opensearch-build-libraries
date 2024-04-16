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
        def gemCommands = getCommands('sh', 'gem')
        assertThat(curlCommands, hasItem(
            "cd /tmp/workspace/dist && curl --fail --data-binary @`ls *.gem` -H 'Authorization:API_KEY' -H 'Content-Type: application/octet-stream' https://rubygems.org/api/v1/gems".toString()
        ))
        assertThat(gemCommands, hasItem("#!/bin/bash\n        source ~/.rvm/scripts/rvm && rvm use 2.6.0 && ruby --version\n        gem cert --add /tmp/workspace/certs/opensearch-rubygems.pem\n        cd /tmp/workspace/dist && gemNameWithVersion=\$(ls *.gem)\n        gem install \$gemNameWithVersion\n        gemName=\$(echo \$gemNameWithVersion | sed -E 's/(-[0-9.]+-*[a-z]*.gem\$)//g')\n        gem uninstall \$gemName\n        gem install \$gemNameWithVersion -P MediumSecurity\n        gem uninstall \$gemName\n        gem install \$gemNameWithVersion -P HighSecurity\n    "))
    }

    @Test
    void testPublishingRubyWithArgs() {
        this.registerLibTester(new PublishToRubyGemsLibTester('ruby-api-key', 'test', 'certificate/path'))
        super.setUp()
        super.testPipeline('tests/jenkins/jobs/PublishToRubyGemWithArgs_Jenkinsfile')
        def curlCommands = getCommands('sh', 'curl')
        def gemCommands = getCommands('sh', 'gem')
        assertThat(curlCommands, hasItem(
            "cd /tmp/workspace/test && curl --fail --data-binary @`ls *.gem` -H 'Authorization:API_KEY' -H 'Content-Type: application/octet-stream' https://rubygems.org/api/v1/gems".toString()))
        assertThat(gemCommands, hasItem("#!/bin/bash\n        source ~/.rvm/scripts/rvm && rvm use jruby-9.3.0.0 && ruby --version\n        gem cert --add /tmp/workspace/certificate/path\n        cd /tmp/workspace/test && gemNameWithVersion=\$(ls *.gem)\n        gem install \$gemNameWithVersion\n        gemName=\$(echo \$gemNameWithVersion | sed -E 's/(-[0-9.]+-*[a-z]*.gem\$)//g')\n        gem uninstall \$gemName\n        gem install \$gemNameWithVersion -P MediumSecurity\n        gem uninstall \$gemName\n        gem install \$gemNameWithVersion -P HighSecurity\n    "))
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
