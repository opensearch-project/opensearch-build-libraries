/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@11.2.0', retriever: legacySCM(scm))

    def secret_artifacts = [
        [envVar: 'ARTIFACT_BUCKET_NAME', secretRef: 'op://opensearch-infra-secrets/aws-resource-arns/jenkins-artifact-bucket-name'],
        [envVar: 'AWS_ACCOUNT_PUBLIC', secretRef: 'op://opensearch-infra-secrets/aws-accounts/jenkins-aws-account-public']
    ]

    def sha = getManifestSHA(args)

    withSecrets(secrets: secret_artifacts) {
        withAWS(role: 'opensearch-bundle', roleAccount: "${AWS_ACCOUNT_PUBLIC}", duration: 900, roleSessionName: 'jenkins-session') {
            s3Upload(bucket: "${ARTIFACT_BUCKET_NAME}", file: sha.lock, path: sha.path)
        }
    }
}
