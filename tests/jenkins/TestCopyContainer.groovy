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
import org.junit.*
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString


class TestCopyContainer extends BuildPipelineTest {

    String sourceImage = 'ci-runner:centos7-123'
    String destinationImage = 'ci-runner:centos7-123'

    @Before
    void setUp() {
        binding.setVariable('DOCKER_USERNAME', 'dummy_docker_username')
        binding.setVariable('DOCKER_PASSWORD', 'dummy_docker_password')
        binding.setVariable('ARTIFACT_PROMOTION_ROLE_NAME', 'sample-agent-AssumeRole')
        binding.setVariable('AWS_ACCOUNT_ARTIFACT', '1234567890')
        binding.setVariable('DATA_PREPPER_STAGING_CONTAINER_REPOSITORY', 'sample_dataprepper_ecr_url')
        binding.setVariable('SOURCE_IMAGE_REGISTRY', 'opensearchstaging')
        binding.setVariable('SOURCE_IMAGE', sourceImage)
        binding.setVariable('DESTINATION_IMAGE_REGISTRY', 'opensearchstaging')
        binding.setVariable('DESTINATION_IMAGE', destinationImage)
        binding.setVariable('ALL_TAGS', true)
        helper.registerAllowedMethod('withAWS', [Map, Closure], null)
        super.setUp()

    }

    @Test
    public void testCopyContainerDockerStagingToDockerProd_verifyShellCommand() {
        super.testPipeline("tests/jenkins/jobs/DockerCopy_Jenkinsfile")

        String craneStr = 'set -x && crane cp opensearchstaging/ci-runner:centos7-123 public.ecr.aws/opensearchstaging/ci-runner:centos7-123'
        assertThat(getShellCommands('sh', 'crane'), hasItem(craneStr))

        String dockerStr = 'set +x && docker logout && docker logout public.ecr.aws'
        assertThat(getShellCommands('sh', 'docker logout'), hasItem(dockerStr))
    }

    @Test
    public void testCopyContainerDockerStagingToDockerProdAllTags_verifyShellCommand() {
        super.testPipeline("tests/jenkins/jobs/DockerCopyAllTags_Jenkinsfile")

        String craneAllTagsStr = 'set -x && crane cp opensearchstaging/ci-runner opensearchstaging/ci-runner --all-tags'
        assertThat(getShellCommands('sh', 'crane'), hasItem(craneAllTagsStr))

        String dockerAllTagsStr = 'set +x && docker logout && docker logout public.ecr.aws'
        assertThat(getShellCommands('sh', 'docker logout'), hasItem(dockerAllTagsStr))
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
