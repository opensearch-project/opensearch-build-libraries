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
import org.junit.Before
import org.junit.Test

class TestUploadMinSnapshotsToS3 extends BuildPipelineTest {

    @Before
    void setUp() {
        List <Closure> fileActions = ['createSha512Checksums']
        this.registerLibTester(new UploadMinSnapshotsToS3LibTester( fileActions, 'tests/data/opensearch-1.3.0.yml', 'tar' ))
        super.setUp()
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('ARTIFACT_PROMOTION_ROLE_NAME', "ARTIFACT_PROMOTION_ROLE_NAME")
        binding.setVariable('AWS_ACCOUNT_ARTIFACT', "AWS_ACCOUNT_ARTIFACT")
        binding.setVariable('ARTIFACT_PRODUCTION_BUCKET_NAME', "ARTIFACT_PRODUCTION_BUCKET_NAME")
    }

    @Test
    public void test() {
        super.testPipeline("tests/jenkins/jobs/uploadMinSnapshotsToS3_Jenkinsfile")
    }  
}
