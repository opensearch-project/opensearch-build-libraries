/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.BuildManifest
import jenkins.Messages

void call(Map args = [:]) {
    def buildManifest = new BuildManifest(readYaml(file: args.buildManifestFileName))

    String buildId = buildManifest.build.id
    echo "Build Id: ${buildId}"

    def artifactPath = buildManifest.getArtifactRoot(args.jobName, buildId)
    def uploadPath = "${artifactPath}/test-results"
    if (args.buildNumber){
        uploadPath = "${artifactPath}/test-results/${args.buildNumber}"
    }
    withCredentials([
        string(credentialsId: 'jenkins-artifact-bucket-name', variable: 'ARTIFACT_BUCKET_NAME'),
        string(credentialsId: 'jenkins-aws-account-public', variable: 'AWS_ACCOUNT_PUBLIC')]) {
            echo "Uploading to s3://${ARTIFACT_BUCKET_NAME}/${artifactPath}"

            withAWS(role: 'opensearch-test', roleAccount: "${AWS_ACCOUNT_PUBLIC}", duration: 900, roleSessionName: 'jenkins-session') {
                s3Upload(file: 'test-results', bucket: "${ARTIFACT_BUCKET_NAME}", path: uploadPath)
            }
        }

    def baseUrl = buildManifest.getArtifactRootUrl("${PUBLIC_ARTIFACT_URL}", args.jobName)
    new Messages(this).add("${STAGE_NAME}", "${baseUrl}/test-results/")
}
