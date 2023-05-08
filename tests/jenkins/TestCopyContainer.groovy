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
        binding.setVariable('RECURSIVE_COPY', true)
        helper.registerAllowedMethod('withAWS', [Map, Closure], null)
        super.setUp()

    }

    @Test
    public void testCopyContainerDockerStagingToDockerProd_verifyShellCommand() {
        super.testPipeline("tests/jenkins/jobs/DockerCopy_Jenkinsfile")

        String gcrane_str = '''\n        set +x\n\n        if [ false = true ]; then\n            echo \"Copying all image tags recursively from opensearchstaging/alpine to opensearchproject/alpine\"\n            for source_entry in `gcrane ls opensearchstaging/alpine`; do\n                image_tag=`echo $source_entry | cut -d/ -f3 | cut -d: -f2`\n                destination_entry=\"opensearchproject/alpine:$image_tag\"\n                gcrane cp $source_entry $destination_entry\n            done\n        else\n            echo \"Copying single image tag from opensearchstaging/alpine:3.15.4 to opensearchproject/alpine:3.15.4\"\n            gcrane cp opensearchstaging/alpine:3.15.4 opensearchproject/alpine:3.15.4\n        fi\n\n        docker logout\n        docker logout opensearchproject\n    '''
        assertThat(getShellCommands('sh', 'gcrane'), hasItem(gcrane_str))
    }

    @Test
    public void testCopyContainerDockerStagingToDockerProdRecursive_verifyShellCommand() {
        super.testPipeline("tests/jenkins/jobs/DockerCopyRecursive_Jenkinsfile")

        String gcrane_recursive_str = '''\n        set +x\n\n        if [ true = true ]; then\n            echo \"Copying all image tags recursively from opensearchstaging/alpine to opensearchproject/alpine\"\n            for source_entry in `gcrane ls opensearchstaging/alpine`; do\n                image_tag=`echo $source_entry | cut -d/ -f3 | cut -d: -f2`\n                destination_entry=\"opensearchproject/alpine:$image_tag\"\n                gcrane cp $source_entry $destination_entry\n            done\n        else\n            echo \"Copying single image tag from opensearchstaging/alpine:3.15.4 to opensearchproject/alpine:3.15.4\"\n            gcrane cp opensearchstaging/alpine:3.15.4 opensearchproject/alpine:3.15.4\n        fi\n\n        docker logout\n        docker logout opensearchproject\n    '''

        assertThat(getShellCommands('sh', 'gcrane'), hasItem(gcrane_recursive_str))
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
