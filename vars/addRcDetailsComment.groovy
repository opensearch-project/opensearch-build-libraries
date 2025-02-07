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
 *  @param args.version <required> - Release version.
 *  @param args.opensearchRcNumber <optional> - OpenSearch RC number. eg: 5. Defaults to latest RC number
 *  @param args.opensearchDashboardsRcNumber <optional> - OpenSearch-Dashboards RC number. eg: 5. Defaults to latest RC number
 * */
void call(Map args = [:]) {
    def buildIndexName = 'opensearch-distribution-build-results'
    def version = args.version
    def opensearchRcNumber
    def opensearchDashboardsRcNumber
    def opensearchRcBuildNumber
    def opensearchDashboardsRcBuildNumber
    def releaseIssueUrl

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

            ReleaseCandidateStatus releaseCandidateStatus = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, version, this)
            ReleaseMetricsData releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)

            releaseIssueUrl = releaseMetricsData.getReleaseIssue('opensearch-build')
            opensearchRcNumber = args.opensearchRcNumber ?: releaseCandidateStatus.getLatestRcNumber('OpenSearch')
            opensearchDashboardsRcNumber = args.opensearchDashboardsRcNumber ?: releaseCandidateStatus.getLatestRcNumber('OpenSearch-Dashboards')
            opensearchRcBuildNumber = releaseCandidateStatus.getRcDistributionNumber(opensearchRcNumber, 'OpenSearch').toString()
            opensearchDashboardsRcBuildNumber = releaseCandidateStatus.getRcDistributionNumber(opensearchDashboardsRcNumber, 'OpenSearch-Dashboards').toString()
        }
    }

    def rcValues = [
            VERSION: version,
            OPENSEARCH_RC_NUMBER: opensearchRcNumber,
            OPENSEARCH_DASHBOARDS_RC_NUMBER: opensearchDashboardsRcNumber,
            OPENSEARCH_RC_BUILD_NUMBER: opensearchRcBuildNumber,
            OPENSEARCH_DASHBOARDS_RC_BUILD_NUMBER: opensearchDashboardsRcBuildNumber
    ]

    try {
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
