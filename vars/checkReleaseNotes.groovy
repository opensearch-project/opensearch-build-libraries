/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.ReleaseMetricsData
import jenkins.ParseReleaseNotesMarkdownTable
import utils.TemplateProcessor
/**
 * Library to check and notify missing release notes.
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <required> - Release version. eg: 3.0.0
 * @param args.dataTable <required> - Markdown data table file in the format: https://github.com/opensearch-project/opensearch-build/issues/3747#issuecomment-2704366641 eg: ./table.md
 * @param args.action <optional> - Action to perform. Default is 'check'. Acceptable values are 'check' and 'notify'.
 */
void call(Map args = [:]) {
    String action = args.action ?: 'check'
    // Parameter check
    validateParameters(args, action)
    def version = args.version.tokenize('-')[0]

    def parsedContent = new ParseReleaseNotesMarkdownTable(readFile(args.dataTable)).parseReleaseNotesMarkdownTableRows()
    def componentsWithFalseStatus = parsedContent.findAll { it['Status'] == 'False' }
    echo("Components missing release notes: " + componentsWithFalseStatus.collect { it['Component'] })

    if (action == 'notify' && !componentsWithFalseStatus.isEmpty()) {
        echo "Notifying all the components with missing release notes."
        notifyReleaseOwners(version, componentsWithFalseStatus)
    }
}

/**
 * Validates input parameters
 */
private void validateParameters(Map args, String action) {
    if (!args.version) {
        error "Version parameter is required."
    }

    if (!args.dataTable || args.dataTable.isEmpty()) {
        error "dataTable is required to get the content."
    } else {
        if (!fileExists(args.dataTable)) {
            error("Invalid path.Data Table file does not exist at ${args.dataTable}")
        }
    }

    List<String> validActions = ['check', 'notify']
    if (!validActions.contains(action)) {
        error "Invalid action '${action}'. Valid values: ${validActions.join(', ')}"
    }
}

/**
 * Notify components regarding the missing release notes by adding a comment to the release issue.
 * @param version: Release version.
 * @param componentsWithFalseStatus: Parsed content with status as False.
 */
private void notifyReleaseOwners(String version,def componentsWithFalseStatus) {
    componentsWithFalseStatus.each { component ->
        withCredentials([
                string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
                string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')]) {
            withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
                def metricsUrl = env.METRICS_HOST_URL
                def awsAccessKey = env.AWS_ACCESS_KEY_ID
                def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
                def awsSessionToken = env.AWS_SESSION_TOKEN

                ReleaseMetricsData releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)
                def componentName = component["Component"]
                if(componentName != 'functionalTestDashboards') {
                    def releaseIssueUrl = releaseMetricsData.getReleaseIssue("${componentName}", "component.keyword")
                    def bindings = [
                            BRANCH: component["Branch"]
                    ]
                    def ghCommentContent = new TemplateProcessor(this).process("release/missing-release-notes.md", bindings, "${WORKSPACE}")
                    addComment(releaseIssueUrl, ghCommentContent)
                }
            }
        }
    }
}

/**
 * Add a comment on the Release issue.
 * @param releaseIssueUrl: Component release issue URL.
 * @param commentBodyFile: Path to the file containing GitHub comment content.
 */
private void addComment(String releaseIssueUrl, def commentBodyFile) {
    withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
        sh(
                script: "gh issue comment ${releaseIssueUrl} --body-file ${commentBodyFile}",
                returnStdout: true
        )
    }
}
