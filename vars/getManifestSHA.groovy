/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
Map call(Map args = [:]) {
    def secret_artifacts = [
        [envVar: 'ARTIFACT_BUCKET_NAME', secretRef: 'op://opensearch-infra-secrets/aws-resource-arns/jenkins-artifact-bucket-name'],
        [envVar: 'AWS_ACCOUNT_PUBLIC', secretRef: 'op://opensearch-infra-secrets/aws-accounts/jenkins-aws-account-public']
    ]
    String inputManifest = args.inputManifest ?: "manifests/${INPUT_MANIFEST}"
    String jobName = args.jobName ?: "${JOB_NAME}"

    buildManifest(
        args + [
            inputManifest: inputManifest,
            lock: true
        ]
    )

    String manifestLock = "${inputManifest}.lock"
    String manifestSHA = sha1(manifestLock)
    echo "Manifest SHA: ${manifestSHA}"

    def lib = library(identifier: 'jenkins@11.3.0', retriever: legacySCM(scm))
    def inputManifestObj = lib.jenkins.InputManifest.new(readYaml(file: manifestLock))
    String shasRoot = inputManifestObj.getSHAsRoot(jobName)
    String manifestSHAPath = "${shasRoot}/${manifestSHA}.yml"
    echo "Manifest lock: ${manifestLock}"
    echo "Manifest SHA path: ${manifestSHAPath}"

    Boolean manifestSHAExists = false
    withSecrets(secrets: secret_artifacts) {
        withAWS(role: 'opensearch-bundle', roleAccount: "${AWS_ACCOUNT_PUBLIC}", duration: 900, roleSessionName: 'jenkins-session') {
            if (s3DoesObjectExist(bucket: "${ARTIFACT_BUCKET_NAME}", path: manifestSHAPath)) {
                manifestSHAExists = true
        }
    }
    }

    echo "Manifest SHA exists: ${manifestSHAExists}"

    return [
        sha: manifestSHA,
        lock: manifestLock,
        path: manifestSHAPath,
        exists: manifestSHAExists
    ]
}
