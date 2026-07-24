/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.ReleaseMetricsData
import utils.TemplateProcessor
/**
 * Library to check and notify missing release notes.
 * The release notes status is sourced from the opensearch_release_metrics index (release_notes boolean
 * per component), the same index that backs the other release chores, instead of parsing a markdown table.
 * @param Map args = [:] args A map of the following parameters
 * @param args.inputManifest <required> - Input manifest file(s) eg: [manifests/2.0.0/opensearch-2.0.0.yml, manifests/2.0.0/opensearch-dashboards-2.0.0.yml] .
 * @param args.action <optional> - Action to perform. Default is 'check'. Acceptable values are 'check' and 'notify'.
 * @return List of component names missing release notes (empty when all components are ready).
 */
List<String> call(Map args = [:]) {
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

    String action = args.action ?: 'check'
    def inputManifest = args.inputManifest
    // Parameter check
    validateParameters(args, action)

    List<String> componentsMissingReleaseNotes = []

    inputManifest.each { inputManifestFile ->
        def inputManifestObj = readYaml(file: inputManifestFile)
        def version = inputManifestObj.build.version.tokenize('-')[0]
        withSecrets(secrets: secret_metrics_cluster){
            withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
                def metricsUrl = env.METRICS_HOST_URL
                def awsAccessKey = env.AWS_ACCESS_KEY_ID
                def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
                def awsSessionToken = env.AWS_SESSION_TOKEN

                ReleaseMetricsData releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)
                inputManifestObj.components.each { component ->
                    def releaseNotesExist = releaseMetricsData.getReleaseNotesStatus(component.name)
                    // Conservative gate: only a confirmed `true` clears a component. A missing metrics doc
                    // or a query failure returns null and is flagged as missing so releases never pass silently.
                    if (releaseNotesExist != true) {
                        componentsMissingReleaseNotes.add(component.name)
                        if (action == 'notify') {
                            notifyReleaseOwners(releaseMetricsData, component.name)
                        }
                    }
                }
            }
        }
    }

    echo("Components missing release notes: ${componentsMissingReleaseNotes}")
    return componentsMissingReleaseNotes
}

/**
 * Validates input parameters
 */
private void validateParameters(Map args, String action) {
    if (!args.inputManifest || args.inputManifest.isEmpty()) {
        error "inputManifest parameter is required."
    } else {
        args.inputManifest.each { inputManifestFile ->
            if (!fileExists(inputManifestFile)) {
                error("Invalid path. Input manifest file does not exist at ${inputManifestFile}")
            }
        }
    }

    List<String> validActions = ['check', 'notify']
    if (!validActions.contains(action)) {
        error "Invalid action '${action}'. Valid values: ${validActions.join(', ')}"
    }
}

/**
 * Notify a component regarding the missing release notes by adding a comment to its release issue.
 * @param releaseMetricsData: Data accessor used to look up the component's release issue.
 * @param componentName: Component missing release notes.
 */
private void notifyReleaseOwners(ReleaseMetricsData releaseMetricsData, String componentName) {
    def releaseIssueUrl = releaseMetricsData.getReleaseIssue(componentName, "component.keyword")
    if (releaseIssueUrl == null || releaseIssueUrl == 'null') {
        echo("No release issue found for ${componentName}. Skipping notification.")
        return
    }
    def ghCommentContent = new TemplateProcessor(this).process("release/missing-release-notes.md", [:], "${WORKSPACE}")
    addComment(releaseIssueUrl, ghCommentContent)
}

/**
 * Add a comment on the Release issue.
 * @param releaseIssueUrl: Component release issue URL.
 * @param commentBodyFile: Path to the file containing GitHub comment content.
 */
private void addComment(String releaseIssueUrl, def commentBodyFile) {
    def secret_github_bot = [
        [envVar: 'GITHUB_USER', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-username'],
        [envVar: 'GITHUB_TOKEN', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-token']
    ]

    withSecrets(secrets: secret_github_bot){
        sh(
                script: "gh issue comment ${releaseIssueUrl} --body-file ${commentBodyFile}",
                returnStdout: true
        )
    }
}
