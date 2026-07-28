/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.ComponentRepoData
import jenkins.ReleaseMetricsData
import java.time.LocalDate
import utils.TemplateProcessor
/**
 * Library to check and notify missing code coverage.
 * @param Map args = [:] args A map of the following parameters
 * @param args.inputManifest <required> - Array of input manifest(s). eg: ["manifests/2.0.0/opensearch-2.0.0.yml", "manifests/2.0.0/opensearch-dashboards-2.0.0.yml"]
 * @param args.action <optional> - Action to perform. Default is 'check'. Acceptable values are 'check' and 'notify'.
 * @return List of component names missing code coverage (empty when all components are reporting coverage).
 */
List<String> call(Map args = [:]) {
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

    String action = args.action ?: 'check'
    // Parameter check
    validateParameters(args, action)
    def inputManifests = args.inputManifest
    def inputManifestYaml = readYaml(file: args.inputManifest[0])
    def version = inputManifestYaml.build.version
    version = version.tokenize('-')[0] // Get only version and skip the qualifier
    def now = LocalDate.now()
    def monthYear = String.format("%02d-%d", now.monthValue, now.year)
    def codeCoverageIndex = "opensearch-codecov-metrics-${monthYear}"
    def componentsMissingCodeCoverageWithUrl = [:]

    inputManifests.each { inputManifestFile ->
        def inputManifestObj = readYaml(file: inputManifestFile)
        withSecrets(secrets: secret_metrics_cluster){
            withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
                def metricsUrl = env.METRICS_HOST_URL
                def awsAccessKey = env.AWS_ACCESS_KEY_ID
                def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
                def awsSessionToken = env.AWS_SESSION_TOKEN

                def componentRepoData = new ComponentRepoData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)
                def releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)
                inputManifestObj.components.each { component ->
                    String repoName = component.repository.toString().split('/')[-1].replace('.git', '')
                    def codeCoverage = componentRepoData.getCodeCoverage(component.name, codeCoverageIndex)
                    def releaseIssue = releaseMetricsData.getReleaseIssue(repoName)
                    // Conservative gate: a component clears only when coverage is confirmed reporting.
                    // A null/empty result (query failure or no metrics doc) or an explicit "no-coverage"
                    // state (equivalent to codeCoverage.coverage == 0) flags the component as missing so
                    // releases never pass silently.
                    if (codeCoverage == null || codeCoverage.isEmpty() || codeCoverage.state == "no-coverage") {
                        componentsMissingCodeCoverageWithUrl[component.name] = codeCoverage?.url
                        if (args.action == 'notify' && codeCoverage?.state == "no-coverage") {
                            notifyReleaseOwners(component.name, codeCoverage, releaseIssue)
                        }
                    }
                }
            }
        }
    }

    if (componentsMissingCodeCoverageWithUrl) {
        echo("Components missing code coverage are: ${componentsMissingCodeCoverageWithUrl}")
    } else {
        echo('All components are reporting code coverage.')
    }

    return componentsMissingCodeCoverageWithUrl.keySet().toList()
}

/**
 * Validates input parameters
 */
private void validateParameters(Map args, action) {
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
 * Notify components regarding the missing code coverage adding a comment to the release issue.
 * @param codeCoverage: Map of args with with branch and url.
 * @param releaseIssue: GitHub release issue URL
 */
private void notifyReleaseOwners(String componentName, Map codeCoverage, String releaseIssue) {
    try {
        def bindings = [
                BRANCH: codeCoverage.branch,
                CODECOV_URL: codeCoverage.url,
                COMPONENT_NAME: componentName
        ]
        def ghCommentBodyContent = new TemplateProcessor(this).process("release/missing-code-coverage.md", bindings, "${WORKSPACE}")
        addComment(releaseIssue, ghCommentBodyContent)
    } catch (Exception e) {
        error("Failed to process template: ${e.getMessage()}")
    }
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

