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


class TestDetectTestDockerAgent extends BuildPipelineTest {

    @Test
    public void test() {
        this.registerLibTester(new DetectTestDockerAgentLibTester(
            'tests/data/opensearch-1.3.0-test.yml'
        ))
        super.testPipeline("tests/jenkins/jobs/DetectTestDockerAgent_Jenkinsfile")
    }
}
