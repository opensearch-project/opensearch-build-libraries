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


class TestUploadToS3 extends BuildPipelineTest {

    @Before
    void setUp() {

        this.registerLibTester(new UploadToS3LibTester( '/tmp/src/path', 'dummy_bucket', '/upload/path' ))

        super.setUp()

        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('AWS_ACCOUNT_PUBLIC', "AWS_ACCOUNT_PUBLIC")
    }

    @Test
    void testUploadToS3() {
        super.testPipeline("tests/jenkins/jobs/UploadToS3_Jenkinsfile")
    }
}
