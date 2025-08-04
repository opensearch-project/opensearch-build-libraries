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

class TestUploadTestResults extends BuildPipelineTest {

    @Before
    void setUp() {

        this.registerLibTester(new UploadTestResultsLibTester( 'tests/data/opensearch-1.3.0-build.yml', 'dummy_job'))

        super.setUp()

        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('ARTIFACT_BUCKET_NAME', "ARTIFACT_BUCKET_NAME")
        binding.setVariable('AWS_ACCOUNT_PUBLIC', "AWS_ACCOUNT_PUBLIC")
    }

    @Test
    void testUploadToS3() {
        super.testPipeline("tests/jenkins/jobs/UploadTestResults_Jenkinsfile")
    }
}
