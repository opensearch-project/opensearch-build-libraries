/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/** Library to create, update and close GitHub issue across opensearch-project repositories for integration test failures.
 @param Map args = [:] args A map of the following parameters
 @param args.inputManifestPath <required> - Path to build manifest.
 @param args.distributionBuildNumber <optional> - Distribution build number used to run integTest. Defaults to latest build for given version.
 */

import jenkins.ComponentBuildStatus
import jenkins.ComponentIntegTestStatus
import jenkins.CreateIntegTestMarkDownTable

void call(Map args = [:]) {
    def inputManifest = readYaml(file: args.inputManifestPath)
    def version = inputManifest.build.version
    def product = inputManifest.build.name

    List<String> failedComponents = []
    List<String> passedComponents = []

    withCredentials([
            string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
            string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')]) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN
            def integTestIndexName = 'opensearch-integration-test-results'
            def buildIndexName = 'opensearch-distribution-build-results'
            def distributionBuildNumber = args.distributionBuildNumber ?: new ComponentBuildStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, product, version, this).getLatestDistributionBuildNumber().toString()
            ComponentIntegTestStatus componentIntegTestStatus = new ComponentIntegTestStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, integTestIndexName, product, version, distributionBuildNumber, this)

            passedComponents = componentIntegTestStatus.getComponents('passed')
            failedComponents = componentIntegTestStatus.getComponents('failed')

            failedComponents = failedComponents.unique()
            passedComponents = passedComponents.unique()

            for (component in inputManifest.components) {
                if (failedComponents.contains(component.name)) {
                    println("Integration test failed for ${component.name}, creating github issue")
                    def testData = []
                    def queryData = componentIntegTestStatus.getComponentIntegTestFailedData(component.name)
                    def totalHits = queryData.hits.hits.collect { it._source }
                    totalHits.each { hit ->
                        def rowData = [
                                platform                : hit.platform,
                                distribution            : hit.distribution,
                                architecture            : hit.architecture,
                                test_report_manifest_yml: hit.test_report_manifest_yml,
                                integ_test_build_url    : hit.integ_test_build_url
                        ]
                        testData << rowData
                    }
                    def markdownContent = new CreateIntegTestMarkDownTable(version, testData).create()
                    createGithubIssue(
                            repoUrl: component.repository,
                            issueTitle: "[AUTOCUT] Integration Test Failed for ${component.name}-${version}",
                            issueBody: markdownContent,
                            label: "autocut,v${version}",
                            issueEdit: true
                    )
                }
                if (passedComponents.contains(component.name) && !failedComponents.contains(component.name)) {
                    println("Integration tests passed for ${component.name}, closing github issue")
                    ghIssueBody = """Closing the issue as the integration tests for ${component.name} passed for version: **${version}**.""".stripIndent()
                    closeGithubIssue(
                            repoUrl: component.repository,
                            issueTitle: "[AUTOCUT] Integration Test Failed for ${component.name}-${version}",
                            closeComment: ghIssueBody
                    )
                }
                sleep(time: 3, unit: 'SECONDS')
            }
        }
    }
}
