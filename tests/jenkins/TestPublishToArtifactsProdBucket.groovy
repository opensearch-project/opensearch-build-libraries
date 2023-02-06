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
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

class TestPublishToArtifactsProdBucket extends BuildPipelineTest {
    @Override
    @Before
    void setUp() {

        this.registerLibTester(new PublishToArtifactsProdBucketLibTester('test-role', 'the-windows-msi.msi', 'msi/', 'windows', 'null', true))
        this.registerLibTester(new PublishToArtifactsProdBucketLibTester('test-role-2', 'reporting-cli-2.3.0.tg.gz', 'reporting-cli/'))
        super.setUp()
    }

    @Test
    public void test() {
        super.testPipeline('tests/jenkins/jobs/PublishToArtifactsProdBucket_Jenkinsfile')
    }

    @Test
    void 'verify signing_with_defaults'(){
        runScript('tests/jenkins/jobs/PublishToArtifactsProdBucket_Jenkinsfile')
        assertThat(getShellCommands('sh', 'sign.sh'), hasItem('\n                   #!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_CLIENT_ROLE\n                   export EXTERNAL_ID=SIGNER_CLIENT_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_CLIENT_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_CLIENT_SIGNED_BUCKET\n\n                   /tmp/workspace/opensearch-build/sign.sh reporting-cli-2.3.0.tg.gz --platform linux --sigtype .sig\n               '))
    }

    @Test
    void 'verify_signing_with_args'(){
        runScript('tests/jenkins/jobs/PublishToArtifactsProdBucket_Jenkinsfile')
        assertThat(getShellCommands('sh', 'sign.sh'), hasItem("\n                   #!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_WINDOWS_ROLE\n                   export EXTERNAL_ID=SIGNER_WINDOWS_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_WINDOWS_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_WINDOWS_SIGNED_BUCKET\n                   export PROFILE_IDENTIFIER=SIGNER_WINDOWS_PROFILE_IDENTIFIER\n                   export PLATFORM_IDENTIFIER=SIGNER_WINDOWS_PLATFORM_IDENTIFIER\n\n                   /tmp/workspace/opensearch-build/sign.sh the-windows-msi.msi --platform windows --sigtype null --overwrite \n               "))
    }

    @Test
    void 'verifyS3uploads'(){
        runScript('tests/jenkins/jobs/PublishToArtifactsProdBucket_Jenkinsfile')
        assertThat(getShellCommands('s3Upload', ''), hasItems('{file=the-windows-msi.msi, bucket=ARTIFACT_PRODUCTION_BUCKET_NAME, path=msi/}', '{file=reporting-cli-2.3.0.tg.gz, bucket=ARTIFACT_PRODUCTION_BUCKET_NAME, path=reporting-cli/}'))
    }
    def getShellCommands(methodName, searchString) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == methodName
        }.collect { call ->
            callArgsToString(call)
        }.findAll { command ->
            command.contains(searchString)
        }
        return shCommands
    }
}
