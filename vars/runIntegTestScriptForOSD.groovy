/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/**@
 * Contains logic to invoke runIntegTestScript script for OSD integration tests
 *
 * @param args A map of the following parameters
 * @param args.localComponent <required> The component name from OpenSearch-Dashboards manifest
 * @param args.switchUserNonRoot <required> Run as root user
 * @param args.ciGroup <required> The particular ci-group number to run the integration tests for
 * @param args.artifactPathOpenSearch <required> OpenSearch artifact path
 * @param args.artifactPath <required> OpenSearch-Dashboards artifact path
 * @param args.artifactBucketName <required> S3 build bucket name that contains all the required artifacts
 * @param args.distribution <required> Distribution type (tar/zip/rpm/deb)
 * @param args.buildManifest <required> OS and OSD distribution build manifest path
 * @param args.testManifest <required> OSD test manifest for release version
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))

    unstash "integtest-opensearch-dashboards-${BUILD_NUMBER}"

    if (env.platform == 'windows') {
        echo "On Windows Platform, unstash repository and download the artifacts"
        echo "Downloading from S3: ${args.artifactPathOpenSearch}"
        downloadFromS3(
                assumedRoleName: 'opensearch-bundle',
                roleAccountNumberCred: 'jenkins-aws-account-public',
                downloadPath: "${args.artifactPathOpenSearch}/",
                bucketName: args.artifactBucketName,
                localPath: "${WORKSPACE}/artifacts",
                force: true
        )
        sh("cp -a ${WORKSPACE}/artifacts/${artifactPathOpenSearch} ${WORKSPACE}")

        echo "Downloading from S3: ${artifactPath}"
        downloadFromS3(
                assumedRoleName: 'opensearch-bundle',
                roleAccountNumberCred: 'jenkins-aws-account-public',
                downloadPath: "${args.artifactPath}/",
                bucketName: args.artifactBucketName,
                localPath: "${WORKSPACE}/artifacts",
                force: true
        )
        sh("cp -a ${WORKSPACE}/artifacts/${args.artifactPath} ${WORKSPACE}")
        sh("rm -rf ${WORKSPACE}/artifacts")
    }
    else {
        echo "Not on Windows, unstash repository+artifacts"
    }

    sh("rm -rf test-results")

    runIntegTestScript(
            jobName: "${BUILD_JOB_NAME}",
            componentName: args.localComponent,
            buildManifest: args.buildManifest,
            testManifest: args.testManifest,
            localPath: "${WORKSPACE}/${args.distribution}",
            switchUserNonRoot: args.switchUserNonRoot,
            ciGroup: args.ciGroup
    )
}
