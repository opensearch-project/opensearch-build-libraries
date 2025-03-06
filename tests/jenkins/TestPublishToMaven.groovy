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

class TestPublishToMaven extends BuildPipelineTest {

    @Test
    void testWithDir(){
        this.registerLibTester(new PublishToMavenLibTester('/path/to/signing', '/path/to/artifacts', 'true'))
        super.setUp()
        super.testPipeline("tests/jenkins/jobs/PublishToMaven_Jenkinsfile")

        def signing = getCommands('signArtifacts', '')
        def signing_sh = getCommands('sh', 'sign.sh')
        def release_sh = getCommands('sh', 'stage-maven-release.sh')

        assertThat(signing, hasItem('{artifactPath=/path/to/signing, type=maven, platform=linux, sigtype=.asc}'))
        assertThat(signing_sh, hasItem('#!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_CLIENT_ROLE\n                   export EXTERNAL_ID=SIGNER_CLIENT_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_CLIENT_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_CLIENT_SIGNED_BUCKET\n\n                   workspace/sign.sh /path/to/signing --type maven --platform linux --sigtype .asc\n               '))
        assertThat(release_sh, hasItem('./stage-maven-release.sh /path/to/maven/artifacts true'))
        assertThat(release_sh, hasItem('chmod a+x ./stage-maven-release.sh'))

    }

    @Test
    void testWithManifestYml(){
        this.registerLibTester(new PublishToMavenLibTester('/path/to/signing/manifest.yml', '/path/to/artifacts', 'false'))

        super.setUp()
        super.testPipeline("tests/jenkins/jobs/PublishToMavenManifestYml_Jenkinsfile")

        def signing = getCommands('signArtifacts', '')
        def signing_sh = getCommands('sh', 'sign.sh')
        def release_sh = getCommands('sh', 'stage-maven-release.sh')

        assertThat(signing, hasItem('{artifactPath=/path/to/signing/manifest.yml, type=maven, platform=linux, sigtype=.asc}'))
        assertThat(signing_sh, hasItem('#!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_CLIENT_ROLE\n                   export EXTERNAL_ID=SIGNER_CLIENT_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_CLIENT_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_CLIENT_SIGNED_BUCKET\n\n                   workspace/sign.sh /path/to/signing/manifest.yml --type maven --platform linux --sigtype .asc\n               '))
        assertThat(release_sh, hasItem('./stage-maven-release.sh /path/to/maven/artifacts false'))
        assertThat(release_sh, hasItem('chmod a+x ./stage-maven-release.sh'))
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
