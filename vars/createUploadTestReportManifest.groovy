/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to run the report workflow to generate test-report manifest.
 @param Map args = [:] args A map of the following parameters
 @param args.testManifest <required> - The path of the test manifest used
 @param args.buildManifest <required> - The path of the build manifest of OpenSearch artifacts.
 @param args.dashboardsBuildManifest <optional> - The path of the build manifest of OpenSearch Dashboards
 @param args.testRunID <required> - Test run id of the test workflow being reported.
 @param args.testType <required> - Type of the test workflow being reported.
 @param args.rcNumber <Optional> - The RC Number of the distribution in test workflow being reported.
 @param args.componentName <Optional> - Components that workflow runs on.
 */

def call(Map args = [:]) {
    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))

    if (!parameterCheck(args.testManifest, args.buildManifest, args.testRunID, args.testType)) return null

    def testRunID = args.testRunID;
    def testType = args.testType;
    def rcNumber = args.rcNumber

    def testManifest = lib.jenkins.TestManifest.new(readYaml(file: args.testManifest))
    def buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: args.buildManifest))
    def dashboardsBuildManifest = args.dashboardsBuildManifest ? lib.jenkins.BuildManifest.new(readYaml(file: args.dashboardsBuildManifest)) : null;
    def product = testManifest.name

    echo "Start Reporting workflow for test type: " + buildManifest.getDistribution()

    String paths = generatePaths(testManifest, buildManifest, dashboardsBuildManifest)
    echo "Paths: ${paths}"

    def productBuildManifest = (product.equals("OpenSearch")) ? buildManifest : dashboardsBuildManifest
    String basePath = generateBasePaths(productBuildManifest)
    echo "Base Path ${basePath}"

    String component = args.componentName
    echo "Component: ${component}"

    String reportCommand =
            [
                    './report.sh',
                    "${args.testManifest}",
                    "--artifact-paths ${paths}",
                    "--test-run-id ${testRunID}",
                    "--test-type ${testType}",
                    "--base-path ${basePath}",
                    isNullOrEmpty(rcNumber) ? "" : "--release-candidate ${rcNumber}",
                    isNullOrEmpty(component) ? "" : "--component ${component}",
            ].join(' ')

    echo "Run command: " + reportCommand
    sh(reportCommand)

    String finalUploadPath = generateUploadPath(testManifest, buildManifest, dashboardsBuildManifest, testRunID)
    withCredentials([
            string(credentialsId: 'jenkins-artifact-bucket-name', variable: 'ARTIFACT_BUCKET_NAME'),
            string(credentialsId: 'jenkins-aws-account-public', variable: 'AWS_ACCOUNT_PUBLIC')]) {
        echo "Uploading to s3://${finalUploadPath}"

        withAWS(role: 'opensearch-test', roleAccount: "${AWS_ACCOUNT_PUBLIC}", duration: 900, roleSessionName: 'jenkins-session') {
            s3Upload(file: "$WORKSPACE/test-report.yml", bucket: "${ARTIFACT_BUCKET_NAME}", path: finalUploadPath)
        }
    }
}

String generatePaths(testManifest, buildManifest, dashboardsBuildManifest) {
    String buildId = buildManifest.build.id
    String artifactRootUrl = buildManifest.getArtifactRootUrl('distribution-build-opensearch', buildId)

    String artifactRootUrlDashboards
    if (dashboardsBuildManifest != null) {
        String buildIdDashboards = dashboardsBuildManifest.build.id
        artifactRootUrlDashboards = dashboardsBuildManifest.getArtifactRootUrl('distribution-build-opensearch-dashboards', buildIdDashboards)
    }

    echo "Artifact root URL: ${artifactRootUrl}"

    return !artifactRootUrlDashboards ?
                "opensearch=${artifactRootUrl}" :
                "opensearch=${artifactRootUrl} opensearch-dashboards=${artifactRootUrlDashboards}"

}

String generateBasePaths(buildManifest) {
    return ["${env.PUBLIC_ARTIFACT_URL}", "${env.JOB_NAME}", buildManifest.build.version, buildManifest.build.id, buildManifest.build.platform, buildManifest.build.architecture, buildManifest.build.distribution].join("/")
}

String generateUploadPath(testManifest, buildManifest, dashboardsBuildManifest, testRunID) {
    def product = testManifest.name
    def productBuildManifest = (product.equals("OpenSearch")) ? buildManifest : dashboardsBuildManifest

    String buildId = productBuildManifest.build.id
    echo "Build Id: ${buildId}"

    def artifactPath = productBuildManifest.getArtifactRoot("${env.JOB_NAME}", buildId)
    return [artifactPath, "test-results", testRunID, "integ-test", "test-report.yml"].join("/")
}

boolean isNullOrEmpty(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }

boolean parameterCheck(String testManifest, String buildManifest, String testRunID, String testType) {
    if (isNullOrEmpty(testManifest)) {
        print("Required argument testManifest is null or empty. Skip running report workflow.")
        return false
    }
    if (isNullOrEmpty(buildManifest)) {
        print("Required argument buildManifest is null or empty. Skip running report workflow.")
        return false
    }
    if (isNullOrEmpty(testRunID)) {
        print("Required argument testRunID is null or empty. Skip running report workflow.")
        return false
    }
    if (isNullOrEmpty(testType)) {
        print("Required argument testType is null or empty. Skip running report workflow.")
        return false
    }
    return true
}
