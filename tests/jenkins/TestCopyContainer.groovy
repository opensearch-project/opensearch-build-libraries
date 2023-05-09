/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.tests.BuildPipelineTest
import org.junit.*
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString


class TestCopyContainer extends BuildPipelineTest {

    String sourceImage = 'alpine:3.15.4'
    String destinationImage = 'alpine:3.15.4'

    @Before
    void setUp() {
        binding.setVariable('DOCKER_USERNAME', 'dummy_docker_username')
        binding.setVariable('DOCKER_PASSWORD', 'dummy_docker_password')
        binding.setVariable('ARTIFACT_PROMOTION_ROLE_NAME', 'sample-agent-AssumeRole')
        binding.setVariable('AWS_ACCOUNT_ARTIFACT', '1234567890')
        binding.setVariable('DATA_PREPPER_STAGING_CONTAINER_REPOSITORY', 'sample_dataprepper_ecr_url')
        binding.setVariable('SOURCE_IMAGE_REGISTRY', 'opensearchstaging')
        binding.setVariable('SOURCE_IMAGE', sourceImage)
        binding.setVariable('DESTINATION_IMAGE_REGISTRY', 'opensearchproject')
        binding.setVariable('DESTINATION_IMAGE', destinationImage)
        binding.setVariable('ALL_TAGS', true)
        helper.registerAllowedMethod('withAWS', [Map, Closure], null)
        super.setUp()

    }

    @Test
    public void testCopyContainerDockerStagingToDockerProd_verifyShellCommand() {
        super.testPipeline("tests/jenkins/jobs/DockerCopy_Jenkinsfile")

        String crane_str = 'set -x && crane cp opensearchstaging/alpine:3.15.4 opensearchproject/alpine:3.15.4'
        assertThat(getShellCommands('sh', 'crane'), hasItem(crane_str))

        String docker_str = 'set +x && docker logout && docker logout opensearchproject'
        assertThat(getShellCommands('sh', 'docker'), hasItem(docker_str))
    }

    @Test
    public void testCopyContainerDockerStagingToDockerProdAllTags_verifyShellCommand() {
        super.testPipeline("tests/jenkins/jobs/DockerCopyAllTags_Jenkinsfile")

        String crane_all_tags_str = 'set -x && crane cp opensearchstaging/alpine opensearchproject/alpine --all-tags'
        assertThat(getShellCommands('sh', 'crane'), hasItem(crane_all_tags_str))

        String docker_all_tags_str = 'set +x && docker logout && docker logout opensearchproject'
        assertThat(getShellCommands('sh', 'docker'), hasItem(docker_all_tags_str))
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
