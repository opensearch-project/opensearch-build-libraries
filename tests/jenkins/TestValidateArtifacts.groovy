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
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.CoreMatchers.equalTo
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem

class TestValidateArtifacts extends BuildPipelineTest {

    @Before
    void setUp() {

        this.registerLibTester(new ValidateArtifactsLibTester('1.0.0', 'tar', 'x64', 'linux', 'opensearch'))

        super.setUp()
    }

    @Test
    void validateArtifacts() {
        super.testPipeline('tests/jenkins/jobs/ValidateArtifacts_Jenkinsfile')
    }
}
