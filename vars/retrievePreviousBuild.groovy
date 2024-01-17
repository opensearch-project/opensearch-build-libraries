/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/** Library to download previous build artifacts into the workspace.
 @param Map args = [:] args A map of the following parameters
 @param args.inputManifest <required> - Relative path to input manifest containing all the components to build.
 @param args.jobName <optional> - Relative path to input manifest containing all the components to build.
 @param args.platform <required> - Platform of previous build to retrieve.
 @param args.architecture <required> - Architecture of previous build to retrieve.
 @param args.distribution <required> - Distribution of previous build to retrieve.
 @param args.previousBuildId <optional> - Build id of previous build for incremental build. Defaults to latest.
 */
void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    def inputManifestObj = lib.jenkins.InputManifest.new(readYaml(file: args.inputManifest))

    def DISTRIBUTION_JOB_NAME = args.jobName ? args.jobName : "${JOB_NAME}"
    def version = inputManifestObj.build.version

    def DISTRIBUTION_PLATFORM = args.platform
    def DISTRIBUTION_ARCHITECTURE = args.architecture
    def distribution = args.distribution
    def prefixPath = "${WORKSPACE}/download"
    def DISTRIBUTION_BUILD_NUMBER

    if (args.previousBuildId.equalsIgnoreCase("latest")) {
        DISTRIBUTION_BUILD_NUMBER = sh(
                script:  "curl -sL https://ci.opensearch.org/ci/dbc/${DISTRIBUTION_JOB_NAME}/${version}/index.json | jq -r \".latest\"",
                returnStdout: true
        ).trim()
        //Once we have new index.json, URL will be changed to: https://ci.opensearch.org/ci/dbc/${DISTRIBUTION_JOB_NAME}/${version}/index/${platform}/${architecture}/${distribution}/index.json
    } else {
        DISTRIBUTION_BUILD_NUMBER = args.previousBuildId
    }

    def artifactPath = "${DISTRIBUTION_JOB_NAME}/${version}/${DISTRIBUTION_BUILD_NUMBER}/${DISTRIBUTION_PLATFORM}/${DISTRIBUTION_ARCHITECTURE}/${distribution}"

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
    sh("mkdir -p ${distribution} && mv -v ${prefixPath}/${artifactPath} ${WORKSPACE}")
    if (inputManifestObj.build.getFilename().equals("opensearch")) {
        echo("Setting up Maven Local for OpenSearch build.")
        sh("mkdir -p ~/.m2/repository/org/ && cp -r ${distribution}/builds/opensearch/maven/org/opensearch/ ~/.m2/repository/org/")
    }

}
