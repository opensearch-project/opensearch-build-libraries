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
 * @param args A map of the following parameters
 * @param args.sourceImage <required> The Source Image name and tag <IMAGE_NAME>:<IMAGE_TAG> Eg: opensearch:1.3.2
 * @param args.sourceRegistry <required> The source docker registry, currently supports 'DockerHub' or 'ECR'
 * @param args.destinationImage <required> The Destination Image name and tag <IMAGE_NAME>:<IMAGE_TAG> Eg: opensearch:1.3.2
 * @param args.destinationRegistry <required> The destination docker registry, currently supports 'DockerHub' or 'ECR'
  * @param args.allTags <optional>Copy all the tags of a sourceImage to destinationImage if 'true' and <IMAGE_TAG> is ignored in sourceImage/destinationImage, default to 'false'
 */
void call(Map args = [:]) {
    def secret_dockerhub_staging = [
        [envVar: 'DOCKER_USERNAME', secretRef: 'op://opensearch-infra-secrets/dockerhub-staging-credentials/username'],
        [envVar: 'DOCKER_PASSWORD', secretRef: 'op://opensearch-infra-secrets/dockerhub-staging-credentials/password']
    ]

    def secret_dockerhub_production = [
        [envVar: 'DOCKER_USERNAME', secretRef: 'op://opensearch-infra-secrets/dockerhub-production-credentials/username'],
        [envVar: 'DOCKER_PASSWORD', secretRef: 'op://opensearch-infra-secrets/dockerhub-production-credentials/password']
    ]

    def secret_ecr_production = [
        [envVar: 'ARTIFACT_PROMOTION_ROLE_NAME', secretRef: 'op://opensearch-infra-secrets/aws-iam-roles/jenkins-artifact-promotion-role'],
        [envVar: 'AWS_ACCOUNT_ARTIFACT', secretRef: 'op://opensearch-infra-secrets/aws-accounts/jenkins-aws-production-account']
    ]

    all_tags = args.allTags ?: false
    source_image = args.sourceImage
    source_image_no_tag = source_image.split(':')[0]
    source_registry = args.sourceRegistry
    destination_image = args.destinationImage
    destination_image_no_tag = destination_image.split(':')[0]
    destination_registry = args.destinationRegistry

    if (source_registry == 'opensearchstaging' || destination_registry == 'opensearchstaging') {
        withSecrets(secrets: secret_dockerhub_staging){
            sh("set +x && echo $DOCKER_PASSWORD | docker login --username $DOCKER_USERNAME --password-stdin")
        }
    }

    if (source_registry == 'opensearchproject' || destination_registry == 'opensearchproject') {
        withSecrets(secrets: secret_dockerhub_production){
            sh("set +x && echo $DOCKER_PASSWORD | docker login --username $DOCKER_USERNAME --password-stdin")
        }
    }

    if (source_registry == 'public.ecr.aws/opensearchstaging' || destination_registry == 'public.ecr.aws/opensearchstaging') {
        sh("set +x && aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${destination_registry}")
    }

    if (source_registry == 'public.ecr.aws/opensearchproject' || destination_registry == 'public.ecr.aws/opensearchproject') {
        withSecrets(secrets: secret_ecr_production){
                withAWS(role: "${ARTIFACT_PROMOTION_ROLE_NAME}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                    sh("set +x && aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${destination_registry}")
                }
            }
    }

    craneCopy()

}

def craneCopy() {

    if (all_tags == true) {
        if (destination_registry.contains('opensearchstaging') && destination_image_no_tag.equals('ci-runner')) {
            echo "Copying all the tags of image ${source_registry}/${source_image_no_tag} to ${destination_registry}/${destination_image_no_tag}"
            sh("set -x && crane cp ${source_registry}/${source_image_no_tag} ${destination_registry}/${destination_image_no_tag} --all-tags")
        }
        else {
            error("'destination_registry' must be opensearchstaging registry and 'destination_image' must be ci-runner")
        }
    }
    else {
        echo "Copying single image tag from ${source_registry}/${source_image} to ${destination_registry}/${destination_image}"
        sh("set -x && crane cp ${source_registry}/${source_image} ${destination_registry}/${destination_image}")
    }

    sh("set +x && docker logout && docker logout public.ecr.aws")

    return
}
