/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/** Library to call build.sh script (builds OpenSearch and OpenSearch Dashboards distribution)
 @param Map args = [:] args A map of the following parameters
 @param args.inputManifest <required> - Relative path to input manifest containing all the components to build.
 @param args.platform <required> - Platform of previous build to retrieve.
 @param args.architecture <required> - Architecture of previous build to retrieve.
 @param args.distribution <required> - Distribution of previous build to retrieve.
 @param args.incremental <required> - String value contains the build number of previous build or the latest.
 */
void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@jenkins_incremental', retriever: legacySCM(scm))
    def inputManifestObj = lib.jenkins.InputManifest.new(readYaml(file: args.inputManifest))

    def DISTRIBUTION_JOB_NAME = "${JOB_NAME}"
    def revision = inputManifestObj.build.version

    def DISTRIBUTION_PLATFORM = args.platform
    def DISTRIBUTION_ARCHITECTURE = args.architecture
    def distribution = args.distribution
    def prefixPath = "${WORKSPACE}/download"
    def DISTRIBUTION_BUILD_NUMBER

    if (args.incremental.equalsIgnoreCase("latest")) {
        withCredentials([string(credentialsId: 'jenkins-artifact-bucket-name', variable: 'ARTIFACT_BUCKET_NAME')]) {
            downloadFromS3(
                    assumedRoleName: "opensearch-bundle",
                    roleAccountNumberCred: "jenkins-aws-account-public",
                    downloadPath: "${DISTRIBUTION_JOB_NAME}/${revision}/index.json",
                    bucketName: "${ARTIFACT_BUCKET_NAME}",
                    localPath: "${prefixPath}/index.json",
                    force: true,
            )
        }
        DISTRIBUTION_BUILD_NUMBER = sh(
                script:  "cat ${prefixPath}/index.json | jq -r \".latest\"",
                returnStdout: true
        ).trim()
    } else {
        DISTRIBUTION_BUILD_NUMBER = args.incremental
    }

    def artifactPath = "${DISTRIBUTION_JOB_NAME}/${revision}/${DISTRIBUTION_BUILD_NUMBER}/${DISTRIBUTION_PLATFORM}/${DISTRIBUTION_ARCHITECTURE}/${distribution}"

    withCredentials([string(credentialsId: 'jenkins-artifact-bucket-name', variable: 'ARTIFACT_BUCKET_NAME')]) {
        downloadFromS3(
                assumedRoleName: "opensearch-bundle",
                roleAccountNumberCred: "jenkins-aws-account-public",
                downloadPath: "${artifactPath}/",
                bucketName: "${ARTIFACT_BUCKET_NAME}",
                localPath: "${prefixPath}",
                force: true,
        )
    }
    sh("mkdir -p ${distribution} && mv ${prefixPath}/${artifactPath} ${WORKSPACE}")
    if (inputManifestObj.build.getFilename().equals("opensearch")) {
        echo("Setting up Maven Local for OpenSearch build.")
        sh("mkdir -p ~/.m2/repository/org/ && cp -r ${distribution}/builds/opensearch/maven/org/opensearch/ ~/.m2/repository/org/")
    }

}
