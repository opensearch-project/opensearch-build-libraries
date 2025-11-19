/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

 /**
 * Library to sign and upload artifacts to artifacts.opensearch.org
@param Map[assumedRoleName] <required> - IAM role to be assumed for uploading artifacts
@param Map[source] <required> - Path to yml or artifact file.
@param Map[destination] <required> - Artifact type in the manifest, [type] is required for signing yml.
@param Map[signingPlatform] <optional> - The distribution platform for signing. Defaults to linux
@param Map[sigType] <optional> - signature type. Defaults to '.sig'
@param Map[sigOverwrite]<optional> - Allow output artifacts to overwrite the existing artifacts. Defaults to false
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@11.3.0', retriever: legacySCM(scm))
    println('Signing the artifacts')
    if (args.signingPlatform == 'windows' || args.signingPlatform == 'mac' || args.signingPlatform == 'jar_signer') {
        signArtifacts(
            artifactPath: args.source,
            platform: args.signingPlatform,
            overwrite: args.sigOverwrite ?: true
            )
    } else {
        signArtifacts(
            artifactPath: args.source,
            platform: args.signingPlatform ?: 'linux',
            sigtype: args.sigType ?: '.sig',
            overwrite: args.sigOverwrite ?: false
            )
    }
    println('Uploading the artifacts')

    def secret_artifacts = [
        [envVar: 'AWS_ACCOUNT_ARTIFACT', secretRef: 'op://opensearch-infra-secrets/aws-accounts/jenkins-aws-production-account'],
        [envVar: 'ARTIFACT_PRODUCTION_BUCKET_NAME', secretRef: 'op://opensearch-infra-secrets/aws-resource-arns/jenkins-artifact-production-bucket-name']
    ]

    withSecrets(secrets: secret_artifacts){
            withAWS(role: "${args.assumedRoleName}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                s3Upload(file: "${args.source}", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${args.destination}")
            }
        }
}
