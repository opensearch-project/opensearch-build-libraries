/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /** Library to create, update an close GitHub issue across opensearch-project repositories for distribution build failures.
 @param Map args = [:] args A map of the following parameters
 @param args.inputManifestPath <required> - Path to input manifest.
 @param args.distributionBuildNumber <required> - Distribution build number
 @param args.buildStartTimeFrom <optional> - Start time range to retrieve the documents. Defaults to 'now-6h'
 @param ags.buildStartTimeTo <optional> - End time range to retrieve the documents. Defaults to 'now'
 */

import jenkins.ComponentBuildStatus

void call(Map args = [:]) {
    def inputManifest = readYaml(file: args.inputManifestPath)
    def currentVersion = inputManifest.build.version
    def product = inputManifest.build.name
    def buildStartTimeFrom = args.buildStartTimeFrom ?: 'now-6h'
    def buildStartTimeTo = args.buildStartTimeTo ?: 'now'

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
                    def indexName = 'opensearch-distribution-build-results'

                    ComponentBuildStatus componentBuildStatus = new ComponentBuildStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, product, currentVersion, args.distributionBuildNumber, buildStartTimeFrom, buildStartTimeTo, this)
            
                    passedComponents = componentBuildStatus.getComponents('passed')
                    failedComponents = componentBuildStatus.getComponents('failed')
            }
        }

    failedComponents = failedComponents.unique()
    passedComponents = passedComponents.unique()

    for (component in inputManifest.components) {
        if (failedComponents.contains(component.name)) {
            println("Component ${component.name} failed, creating github issue")
            ghIssueBody = """***Build Failed Error***: **${component.name} failed during the distribution build for version: ${currentVersion}.**
                    Please see build log at ${env.RUN_DISPLAY_URL}.
                    The failed build stage will be marked as unstable :warning: . Please see ./build.sh step for more details.
                    Checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Building-an-OpenSearch-and-OpenSearch-Dashboards-Distribution) to reproduce the failure locally.""".stripIndent()
            createGithubIssue(
                repoUrl: component.repository,
                issueTitle: "[AUTOCUT] Distribution Build Failed for ${component.name}-${currentVersion}",
                issueBody: ghIssueBody,
                label: "autocut,v${currentVersion}",
                issueEdit: true
            )
        }
        if (passedComponents.contains(component.name) && !failedComponents.contains(component.name)) {
            println("Component ${component.name} passed, closing github issue")
            ghIssueBody = """Closing the issue as the distribution build for ${component.name} has passed for version: **${currentVersion}**.
                    Please see build log at ${env.RUN_DISPLAY_URL}""".stripIndent()
            closeGithubIssue(
                repoUrl: component.repository,
                issueTitle: "[AUTOCUT] Distribution Build Failed for ${component.name}-${currentVersion}",
                closeComment: ghIssueBody,
                label: "autocut,v${currentVersion}"
            )
        }
        sleep(time:3, unit:'SECONDS')
    }
}
