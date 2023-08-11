/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to close GitHub issue across opensearch-project repositories.
 @param Map args = [:] args A map of the following parameters
 @param args.testManifest <required> - The path of the test manifest used
 @param args.buildManifest <required> - The path of the build manifest of OpenSearch artifacts.
 @param args.buildManifestDashboards <optional> - The path of the build manifest of OpenSearch Dashboards
 @param args.testRunID <required> - Test run id of the test workflow being reported.
 @param args.testType <required> - Type of the test workflow being reported.
 @param args.componentName <Optional> - Components that workflow runs on.
 */

def call(Map args = [:]) {
    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))

    def testID = args.testRunID;
    def testType = args.testType;

    def testManifest = lib.jenkins.TestManifest.new(readYaml(file: args.testManifest))
    def buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: args.buildManifest))
    def buildManifestDashboards = args.buildManifestDashboards ? lib.jenkins.BuildManifest.new(readYaml(file: args.buildManifestDashboards)) : null;

    echo "Start Reporting workflow for test type: " + buildManifest.getDistribution()

    String paths = generatePaths(testManifest, buildManifest, buildManifestDashboards)
    echo "Paths: ${paths}"

    String basePath = generateBasePaths(buildManifest)
    echo "Base Path ${basePath}"

    String component = args.componentName
    echo "Component: ${component}"

    String reportCommand =
            [
                    './report.sh',
                    "${args.testManifest}",
                    "--artifact-paths ${paths}",
                    "--test-run-id ${testID}",
                    "--test-type ${testType}",
                    "--base-path ${basePath}",
                    "--component ${component}",
            ].join(' ')

    echo "Run command: " + reportCommand
    sh(reportCommand)

    String finalUploadPath = generateUploadPath(testManifest, buildManifest, buildManifestDashboards, testID)
    withCredentials([
            string(credentialsId: 'jenkins-artifact-bucket-name', variable: 'ARTIFACT_BUCKET_NAME'),
            string(credentialsId: 'jenkins-aws-account-public', variable: 'AWS_ACCOUNT_PUBLIC')]) {
        echo "Uploading to s3://${finalUploadPath}"

        withAWS(role: 'opensearch-test', roleAccount: "${AWS_ACCOUNT_PUBLIC}", duration: 900, roleSessionName: 'jenkins-session') {
            s3Upload(file: "$WORKSPACE/test-report.yml", bucket: "${ARTIFACT_BUCKET_NAME}", path: finalUploadPath)
        }
    }
}

String generatePaths(testManifest, buildManifest, buildManifestDashboards) {
    String buildId = buildManifest.build.id
    String artifactRootUrl = buildManifest.getArtifactRootUrl('distribution-build-opensearch', buildId)

    String artifactRootUrlDashboards
    if (buildManifestDashboards != null) {
        String buildIdDashboards = buildManifestDashboards.build.id
        artifactRootUrlDashboards = buildManifestDashboards.getArtifactRootUrl('distribution-build-opensearch-dashboards', buildIdDashboards)
    }

    echo "Artifact root URL: ${artifactRootUrl}"

    return !artifactRootUrlDashboards ?
                "opensearch=${artifactRootUrl}" :
                "opensearch=${artifactRootUrl} opensearch-dashboards=${artifactRootUrlDashboards}"

}

String generateBasePaths(buildManifest) {
    return ["${env.PUBLIC_ARTIFACT_URL}", "${env.JOB_NAME}", buildManifest.build.version, buildManifest.build.id, buildManifest.build.platform, buildManifest.build.architecture, buildManifest.build.distribution].join("/")
}

String generateUploadPath(testManifest, buildManifest, buildManifestDashboards, testID) {
    def product = testManifest.name
    def productBuildManifest = (product.equals("OpenSearch")) ? buildManifest : buildManifestDashboards

    String buildId = productBuildManifest.build.id
    echo "Build Id: ${buildId}"

    def artifactPath = productBuildManifest.getArtifactRoot("${env.JOB_NAME}", buildId)
    return [artifactPath, "test-results", testID, "integ-test", "test-report.yml"].join("/")
}
