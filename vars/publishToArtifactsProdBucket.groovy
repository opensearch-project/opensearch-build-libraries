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
    lib = library(identifier: 'jenkins@2.1.0', retriever: legacySCM(scm))
    println('Signing the artifacts')
    signArtifacts(
            artifactPath: args.source,
            platform: args.signingPlatform ?: 'linux',
            sigtype: args.sigType ?: '.sig',
            overwrite: args.sigOverwrite ?: false
            )
    println('Uploading the artifacts')
    withCredentials([
        string(credentialsId: 'jenkins-aws-production-account', variable: 'AWS_ACCOUNT_ARTIFACT'),
        string(credentialsId: 'jenkins-artifact-production-bucket-name', variable: 'ARTIFACT_PRODUCTION_BUCKET_NAME')]) {
            withAWS(role: "${args.assumedRoleName}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                s3Upload(file: "${args.source}", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${args.destination}")
        }
    }
}