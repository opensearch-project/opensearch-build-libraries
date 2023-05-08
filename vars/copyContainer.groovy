/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/**@
 * Copies a container from one docker registry to another
 *
 * @param [Required] args A map of the following parameters
 * @param [Required] args.recursiveCopy Copy all the tags of a sourceImage to destinationImage if 'true' and <IMAGE_TAG> is ignored in sourceImage/destinationImage, default to 'false'
 * @param [Required] args.sourceImage The Source Image name and tag <IMAGE_NAME>:<IMAGE_TAG> Eg: opensearch:1.3.2
 * @param [Required] args.sourceRegistry The source docker registry, currently supports 'DockerHub' or 'ECR'
 * @param [Required] args.destinationImage The Destination Image name and tag <IMAGE_NAME>:<IMAGE_TAG> Eg: opensearch:1.3.2
 * @param [Required] args.destinationRegistry The destination docker registry, currently supports 'DockerHub' or 'ECR'
 */
void call(Map args = [:]) {

    recursive_copy = args.recursiveCopy ?: false
    source_image = args.sourceImage
    source_image_no_tag = source_image.split(':')[0]
    source_registry = args.sourceRegistry
    destination_image = args.destinationImage
    destination_image_no_tag = destination_image.split(':')[0]
    destination_registry = args.destinationRegistry

    if (args.destinationRegistry == 'opensearchstaging' || args.destinationRegistry == 'opensearchproject') {
        def dockerJenkinsCredential = args.destinationRegistry == 'opensearchproject' ? "jenkins-production-dockerhub-credential" : "jenkins-staging-dockerhub-credential"
        withCredentials([usernamePassword(credentialsId: dockerJenkinsCredential, usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
            def dockerLogin = sh(returnStdout: true, script: "set +x && echo $DOCKER_PASSWORD | docker login --username $DOCKER_USERNAME --password-stdin").trim()
            gcraneCopy()
        }
    }

    if (args.destinationRegistry == 'public.ecr.aws/opensearchproject') {
        withCredentials([
            string(credentialsId: 'jenkins-artifact-promotion-role', variable: 'ARTIFACT_PROMOTION_ROLE_NAME'),
            string(credentialsId: 'jenkins-aws-production-account', variable: 'AWS_ACCOUNT_ARTIFACT')])
            {
                withAWS(role: "${ARTIFACT_PROMOTION_ROLE_NAME}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                    def ecrLogin = sh(returnStdout: true, script: "set +x && aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${args.destinationRegistry}").trim()
                    gcraneCopy()
                }
            }
    }

    if(args.destinationRegistry == 'public.ecr.aws/opensearchstaging') {
        def ecrLogin = sh(returnStdout: true, script: "set +x && aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${args.destinationRegistry}").trim()
        gcraneCopy()
    }

}

def gcraneCopy() {
    sh """
        set +x

        if [ ${recursive_copy} = true ]; then
            echo "Copying all image tags recursively from ${source_registry}/${source_image_no_tag} to ${destination_registry}/${destination_image_no_tag}"
            for source_entry in `gcrane ls ${source_registry}/${source_image_no_tag}`; do
                image_tag=`echo \$source_entry | cut -d/ -f3 | cut -d: -f2`
                destination_entry="${destination_registry}/${destination_image_no_tag}:\$image_tag"
                gcrane cp \$source_entry \$destination_entry
            done
        else
            echo "Copying single image tag from ${source_registry}/${source_image} to ${destination_registry}/${destination_image}"
            gcrane cp ${source_registry}/${source_image} ${destination_registry}/${destination_image}
        fi

        docker logout
        docker logout ${destination_registry}
    """
    return
}
