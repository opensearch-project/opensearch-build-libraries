/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */


import jenkins.tests.BuildPipelineTest
import org.junit.Before
import org.junit.Test


class TestStandardReleasePipeline extends BuildPipelineTest {

    @Before
    void setUp() {
        this.registerLibTester(new StandardReleasePipelineLibTester('AL2-X64', 'test:image'))
        super.setUp()
    }

    @Test
    void testStandardReleasePipeline() {
        super.testPipeline("tests/jenkins/jobs/StandardReleasePipeline_JenkinsFile")
    }
}
