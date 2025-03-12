/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.ReleaseCandidateStatus
import jenkins.ReleaseMetricsData

/** Library to add Release Candidate Details to the release issue
 *  @param Map args = [:] args A map of the following parameters
 *  @param args.version <required> - Release version along with qualifier eg:3.0.0-alpha1.
 *  @param args.opensearchRcNumber <optional> - OpenSearch RC number. eg: 5. Defaults to latest RC number
 *  @param args.opensearchDashboardsRcNumber <optional> - OpenSearch-Dashboards RC number. eg: 5. Defaults to latest RC number
 * */
void call(Map args = [:]) {
    def buildIndexName = 'opensearch-distribution-build-results'
    def version = args.version
    def qualifier = "None"
    def matcher = version =~ /^([\d.]+)(?:-(.+))?$/
    if (matcher) {
        version = matcher[0][1]  // Captures the numeric part (3.0.0)
        qualifier = matcher[0][2] ?: "None" // Captures the qualifier (beta1) or None if no qualifier
    }
    def opensearchRcNumber
    def opensearchDashboardsRcNumber
    def opensearchRcBuildNumber
    def opensearchDashboardsRcBuildNumber
    String releaseIssueUrl

    if (version.isEmpty()){
        error('version is required to get RC details.')
    }
    withCredentials([
            string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
            string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')]) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN

            ReleaseCandidateStatus releaseCandidateStatus = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, version, qualifier, this)
            ReleaseMetricsData releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)

            releaseIssueUrl = releaseMetricsData.getReleaseIssue('opensearch-build')
            opensearchRcNumber = args.opensearchRcNumber ?: releaseCandidateStatus.getLatestRcNumber('OpenSearch')
            opensearchDashboardsRcNumber = args.opensearchDashboardsRcNumber ?: releaseCandidateStatus.getLatestRcNumber('OpenSearch-Dashboards')
            opensearchRcBuildNumber = releaseCandidateStatus.getRcDistributionNumber(opensearchRcNumber, 'OpenSearch').toString()
            opensearchDashboardsRcBuildNumber = releaseCandidateStatus.getRcDistributionNumber(opensearchDashboardsRcNumber, 'OpenSearch-Dashboards').toString()
        }
    }

    def opensearchScanResults = getDockerScanResult('OpenSearch', opensearchRcBuildNumber)
    def opensearchDashboardsScanResults = getDockerScanResult('OpenSearch-Dashboards', opensearchDashboardsRcBuildNumber)

    def rcValues = [
            VERSION: version,
            OPENSEARCH_RC_NUMBER: opensearchRcNumber,
            OPENSEARCH_DASHBOARDS_RC_NUMBER: opensearchDashboardsRcNumber,
            OPENSEARCH_RC_BUILD_NUMBER: opensearchRcBuildNumber,
            OPENSEARCH_DASHBOARDS_RC_BUILD_NUMBER: opensearchDashboardsRcBuildNumber,
            OPENSEARCH_DOCKER_SCAN_RESULTS: opensearchScanResults.dockerScanResult,
            OPENSEARCH_DASHBOARDS_DOCKER_SCAN_RESULTS: opensearchDashboardsScanResults.dockerScanResult,
            OPENSEARCH_DOCKER_SCAN_URL: opensearchScanResults.dockerScanUrl,
            OPENSEARCH_DASHBOARDS_DOCKER_SCAN_URL: opensearchDashboardsScanResults.dockerScanUrl
    ]

    try {
        // Check for null or empty values
        def nullOrEmptyKeys = rcValues.findAll { k, v ->
            v == null || (v instanceof String && v.trim().isEmpty())
        }.keySet()

        if (nullOrEmptyKeys) {
            error "Following required values are null or empty: ${nullOrEmptyKeys.join(', ')}"
        }

        // Load RC details template content
        def templateContent = libraryResource "release/rc-details-template.md"

        // Create binding for template variables
        def binding = [:]
        rcValues.each { k, v -> binding[k] = v }

        // Process template using simple string replacement
        def processedContent = templateContent
        binding.each { key, value ->
            processedContent = processedContent.replace('${' + key + '}', value.toString())
        }

        // Write the processed content
        writeFile(file: "rc-details-comment-body.md", text: processedContent)
    } catch (Exception e) {
        error "Failed to process template: ${e.getMessage()}"
    }
    withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
        println('Adding RC details to the release issue as a comment.')
        sh(
                script: "gh issue comment ${releaseIssueUrl} --body-file 'rc-details-comment-body.md'",
                returnStdout: true
        )
    }
}

def getDockerScanResult(String component, def distributionRcBuildNumber) {
    println('Getting docker scan results')
    String buildJobName = ''
    String JENKINS_BASE_URL = 'https://build.ci.opensearch.org'
    String BLUE_OCEAN_URL = 'blue/rest/organizations/jenkins/pipelines'
    if(component == 'OpenSearch') {
        buildJobName = 'distribution-build-opensearch'
    } else if(component == 'OpenSearch-Dashboards') {
        buildJobName = 'distribution-build-opensearch-dashboards'
    } else {
        error("Invalid component name: ${component}. Valid values: OpenSearch, OpenSearch-Dashboards")
    }
    String dockerScanUrl = sh (
            script: "curl -s -XGET \"${JENKINS_BASE_URL}/${BLUE_OCEAN_URL}/${buildJobName}/runs/${distributionRcBuildNumber}/nodes/\" | jq '.[] | select(.actions[].description? | contains(\"docker-scan\")) | .actions[] | select(.description | contains(\"docker-scan\")) | ._links.self.href'",
            returnStdout: true
    ).trim()
    String artifactsUrl = sh(
            script: "curl -s -XGET \"${JENKINS_BASE_URL}${dockerScanUrl}\" | jq -r '._links.artifacts.href'",
            returnStdout: true
    ).trim()
    String dockerTxtScanUrl = sh(
            script: "curl -s -XGET \"${JENKINS_BASE_URL}${artifactsUrl}\" | jq -r '.[] | select(.name | endswith(\".txt\")) | .url'",
            returnStdout: true
    ).trim()
    String fullDockerTxtScanUrl = "${JENKINS_BASE_URL}${dockerTxtScanUrl}"
    // Do not trim as it messes the text table.
    String dockerScanResult = sh(
            script: "curl -s -XGET \"${fullDockerTxtScanUrl}\"",
            returnStdout: true
    )
    return [dockerScanUrl: fullDockerTxtScanUrl, dockerScanResult: dockerScanResult]
}
