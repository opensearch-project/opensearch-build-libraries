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


class TestSignArtifacts extends BuildPipelineTest {

    @Before
    void setUp() {

        this.registerLibTester(new SignArtifactsLibTester('.sig', 'linux', "${this.workspace}/artifacts", null, null))
        this.registerLibTester(new SignArtifactsLibTester('.rpm', 'linux', "${this.workspace}/artifacts", 'null', null, false))
        this.registerLibTester(new SignArtifactsLibTester(null, 'linux', "${this.workspace}/file.yml", 'maven', null))
        this.registerLibTester(new SignArtifactsLibTester(null, 'windows', "${this.workspace}/the_msi.msi", null, null, true))
        this.registerLibTester(new SignArtifactsLibTester(null, 'mac', "${this.workspace}/the_pkg.pkg", null, null, true))
        this.registerLibTester(new SignArtifactsLibTester(null, 'jar_signer', "${this.workspace}/the_jar.jar", null, null, true))
        super.setUp()
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('ARTIFACT_PROMOTION_ROLE_NAME', 'ARTIFACT_PROMOTION_ROLE_NAME')
        binding.setVariable('AWS_ACCOUNT_ARTIFACT', 'AWS_ACCOUNT_ARTIFACT')
        binding.setVariable('ARTIFACT_PRODUCTION_BUCKET_NAME', 'ARTIFACT_PRODUCTION_BUCKET_NAME')
        binding.setVariable('RPM_SIGNING_ACCOUNT_NUMBER', 'RPM_SIGNING_ACCOUNT_NUMBER')
        binding.setVariable('RPM_RELEASE_SIGNING_PASSPHRASE_SECRETS_ARN', 'RPM_RELEASE_SIGNING_PASSPHRASE_SECRETS_ARN')
        binding.setVariable('RPM_RELEASE_SIGNING_SECRET_KEY_ID_SECRETS_ARN', 'RPM_RELEASE_SIGNING_SECRET_KEY_ID_SECRETS_ARN')
        binding.setVariable('RPM_RELEASE_SIGNING_KEY_ID', 'RPM_RELEASE_SIGNING_KEY_ID')
        binding.setVariable('RPM_SIGNING_PASSPHRASE_SECRETS_ARN', 'RPM_SIGNING_PASSPHRASE_SECRETS_ARN')
        binding.setVariable('RPM_SIGNING_SECRET_KEY_ID_SECRETS_ARN', 'RPM_SIGNING_SECRET_KEY_ID_SECRETS_ARN')
        binding.setVariable('RPM_SIGNING_KEY_ID', 'RPM_SIGNING_KEY_ID')
        binding.setVariable('GITHUB_USER', "GITHUB_USER")
        binding.setVariable('GITHUB_TOKEN', "GITHUB_TOKEN")
        binding.setVariable('SIGNER_WINDOWS_ROLE', 'SIGNER_WINDOWS_ROLE')
        binding.setVariable('SIGNER_WINDOWS_EXTERNAL_ID', 'SIGNER_WINDOWS_EXTERNAL_ID')
        binding.setVariable('SIGNER_WINDOWS_UNSIGNED_BUCKET', 'SIGNER_WINDOWS_UNSIGNED_BUCKET')
        binding.setVariable('SIGNER_WINDOWS_SIGNED_BUCKET', 'SIGNER_WINDOWS_SIGNED_BUCKET')
        binding.setVariable('SIGNER_WINDOWS_PROFILE_IDENTIFIER', 'SIGNER_WINDOWS_PROFILE_IDENTIFIER')
        binding.setVariable('SIGNER_WINDOWS_PLATFORM_IDENTIFIER', 'SIGNER_WINDOWS_PLATFORM_IDENTIFIER')
        binding.setVariable('SIGNER_MAC_ROLE', 'SIGNER_MAC_ROLE')
        binding.setVariable('SIGNER_MAC_EXTERNAL_ID', 'SIGNER_MAC_EXTERNAL_ID')
        binding.setVariable('SIGNER_MAC_UNSIGNED_BUCKET', 'SIGNER_MAC_UNSIGNED_BUCKET')
        binding.setVariable('SIGNER_MAC_SIGNED_BUCKET', 'SIGNER_MAC_SIGNED_BUCKET')
        binding.setVariable('JAR_SIGNER_ROLE', 'JAR_SIGNER_ROLE')
        binding.setVariable('JAR_SIGNER_EXTERNAL_ID', 'JAR_SIGNER_EXTERNAL_ID')
        binding.setVariable('JAR_SIGNER_UNSIGNED_BUCKET', 'JAR_SIGNER_UNSIGNED_BUCKET')
        binding.setVariable('JAR_SIGNER_SIGNED_BUCKET', 'JAR_SIGNER_SIGNED_BUCKET')
        binding.setVariable('SIGNER_CLIENT_ROLE', 'SIGNER_CLIENT_ROLE')
        binding.setVariable('SIGNER_CLIENT_EXTERNAL_ID', 'SIGNER_CLIENT_EXTERNAL_ID')
        binding.setVariable('SIGNER_CLIENT_UNSIGNED_BUCKET', 'SIGNER_CLIENT_UNSIGNED_BUCKET')
        binding.setVariable('SIGNER_CLIENT_SIGNED_BUCKET', 'SIGNER_CLIENT_SIGNED_BUCKET')
    }

    @Test
    void testSignArtifacts() {
        super.testPipeline("tests/jenkins/jobs/SignArtifacts_Jenkinsfile")
    }

    @Test
    void 'verify shell commands'() {
        runScript('tests/jenkins/jobs/SignArtifacts_Jenkinsfile')

        def signCommands = getShellCommands('sign.sh')
        assertThat(signCommands, hasItem('#!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_CLIENT_ROLE\n                   export EXTERNAL_ID=SIGNER_CLIENT_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_CLIENT_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_CLIENT_SIGNED_BUCKET\n\n                   /tmp/workspace/sign.sh /tmp/workspace/artifacts --sigtype .sig --platform linux\n               '))
        assertThat(signCommands, hasItem('#!/bin/bash\n                   set +x\n                   export ROLE=JAR_SIGNER_ROLE\n                   export EXTERNAL_ID=JAR_SIGNER_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=JAR_SIGNER_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=JAR_SIGNER_SIGNED_BUCKET\n                   /tmp/workspace/sign.sh /tmp/workspace/the_jar.jar --platform jar_signer --overwrite \n               '))
    }

    @Test
    void 'verify_overwrite'(){
        runScript('tests/jenkins/jobs/SignArtifacts_Jenkinsfile')

        def signCommands = getShellCommands('sign.sh')
        assertThat(signCommands, hasItem('#!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_WINDOWS_ROLE\n                   export EXTERNAL_ID=SIGNER_WINDOWS_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_WINDOWS_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_WINDOWS_SIGNED_BUCKET\n                   export PROFILE_IDENTIFIER=SIGNER_WINDOWS_PROFILE_IDENTIFIER\n                   export PLATFORM_IDENTIFIER=SIGNER_WINDOWS_PLATFORM_IDENTIFIER\n\n                   /tmp/workspace/sign.sh /tmp/workspace/the_msi.msi --platform windows --overwrite \n               '))
    }

    def getShellCommands(matchstring) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == 'sh'
        }.collect { call ->
            callArgsToString(call)
        }.findAll { command ->
            command.contains(matchstring)
        }
        return shCommands
    }
}
