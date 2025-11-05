/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/* args.buildFeature <Optional> - Replace build.version to a user-defined name on distribution path
 *                                pre: my-workflow-name/3.4.0/11490/linux/x64/tar/builds/opensearch/......
 *                                now: my-workflow-name/<buildFeature>/11490/linux/x64/tar/builds/opensearch/......
 */
void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@11.2.1', retriever: legacySCM(scm))

    def buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: args.buildManifest))
    def minArtifactPath = buildManifest.getMinArtifact()
    def productFilename = buildManifest.build.getFilename()
    def packageName = buildManifest.build.getPackageName()
    def distribution = buildManifest.build.distribution
    def buildVersion = buildManifest.build.version
    def buildFeature = args.buildFeature ?: buildManifest.build.version

    def artifactPath = buildManifest.getArtifactRoot("${JOB_NAME}", "${BUILD_NUMBER}", buildFeature)

    def secret_artifacts = [
        [envVar: 'ARTIFACT_BUCKET_NAME', secretRef: 'op://opensearch-infra-secrets/aws-resource-arns/jenkins-artifact-bucket-name'],
        [envVar: 'ARTIFACT_PRODUCTION_BUCKET_NAME', secretRef: 'op://opensearch-infra-secrets/aws-resource-arns/jenkins-artifact-production-bucket-name'],
        [envVar: 'AWS_ACCOUNT_ARTIFACT', secretRef: 'op://opensearch-infra-secrets/aws-accounts/jenkins-aws-production-account'],
        [envVar: 'ARTIFACT_PROMOTION_ROLE_NAME', secretRef: 'op://opensearch-infra-secrets/aws-iam-roles/jenkins-artifact-promotion-role']
    ]

    withSecrets(secrets: secret_artifacts) {
        echo "Uploading to s3://${ARTIFACT_BUCKET_NAME}/${artifactPath}"

        uploadToS3(
                sourcePath: "${distribution}/builds",
                bucket: "${ARTIFACT_BUCKET_NAME}",
                path: "${artifactPath}/builds"
        )

        uploadToS3(
                sourcePath: "${distribution}/dist",
                bucket: "${ARTIFACT_BUCKET_NAME}",
                path: "${artifactPath}/dist"
        )

        if (buildFeature == buildVersion) {
            echo "Uploading to s3://${ARTIFACT_PRODUCTION_BUCKET_NAME}/${artifactPath}"

            withAWS(role: "${ARTIFACT_PROMOTION_ROLE_NAME}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                s3Upload(file: "${distribution}/builds/${productFilename}/${minArtifactPath}", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "release-candidates/core/${productFilename}/${buildManifest.build.version}/")
                s3Upload(file: "${distribution}/dist/${productFilename}/${packageName}", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "release-candidates/bundle/${productFilename}/${buildManifest.build.version}/")
            }
        }
        else {
            echo "Feature builds, skip publishing to release candidate bucket"
        }
    }

    def baseUrl = buildManifest.getArtifactRootUrl("${PUBLIC_ARTIFACT_URL}", "${JOB_NAME}", "${BUILD_NUMBER}", buildFeature)
    lib.jenkins.Messages.new(this).add("${STAGE_NAME}", [
            "${baseUrl}/builds/${productFilename}/manifest.yml",
            "${baseUrl}/dist/${productFilename}/manifest.yml"
        ].join('\n')
    )
}
