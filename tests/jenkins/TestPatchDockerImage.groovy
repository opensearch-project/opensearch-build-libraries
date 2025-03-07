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


class TestPatchDockerImage extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new PatchDockerImageLibTester(
            'opensearch',
            '1'            )
        )
        super.setUp()
    }

    @Test
    void testPatchDockerImage() {

        super.testPipeline("tests/jenkins/jobs/PatchDockerImage_Jenkinsfile")
    }
}
