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

class TestPublishToPyPi extends BuildPipelineTest {

    @Test
    void testWithDefaults() {
        this.registerLibTester(new PublishToPyPiLibTester('pypi-token'))
        super.setUp()
        super.testPipeline('tests/jenkins/jobs/PublishToPyPi_Jenkinsfile')
        def twineCommands = getCommands('sh', 'twine')
        assertThat(twineCommands, hasItem(
            'twine upload -r pypi dist/*'
        ))

        def signing = getCommands('signArtifacts', '')
        def signing_sh = getCommands('sh', 'sign.sh')
        assertThat(signing, hasItem('{artifactPath=dist, sigtype=.asc, platform=linux}'))
        assertThat(signing_sh, hasItem('\n                   #!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_CLIENT_ROLE\n                   export EXTERNAL_ID=SIGNER_CLIENT_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_CLIENT_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_CLIENT_SIGNED_BUCKET\n\n                   /tmp/workspace/sign.sh dist --sigtype=.asc --platform=linux\n               '))
    }

    @Test
    void testWithCustomDir() {
        this.registerLibTester(new PublishToPyPiLibTester('pypi-token', 'test'))
        super.setUp()
        super.testPipeline('tests/jenkins/jobs/PublishToPyPiWithDir_Jenkinsfile')

        def twineCommands = getCommands('sh', 'twine')
        assertThat(twineCommands, hasItem(
            'twine upload -r pypi test/*'
        ))
        def signing = getCommands('signArtifacts', '')
        assertThat(signing, hasItem('{artifactPath=test, sigtype=.asc, platform=linux}'))

        def signing_sh = getCommands('sh', 'sign.sh')
        assertThat(signing_sh, hasItem('\n                   #!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_CLIENT_ROLE\n                   export EXTERNAL_ID=SIGNER_CLIENT_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_CLIENT_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_CLIENT_SIGNED_BUCKET\n\n                   /tmp/workspace/sign.sh test --sigtype=.asc --platform=linux\n               '))
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
