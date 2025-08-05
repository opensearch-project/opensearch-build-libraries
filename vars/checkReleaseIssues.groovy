/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.ReleaseMetricsData
 /**
 * Library to check if release issue exists in the component repo.
 * @param Map args = [:] args A map of the following parameters
 * @param args.inputManifest <required> - Input manifest file(s) eg: [manifests/2.0.0/opensearch-2.0.0.yml,manifests/2.0.0/opensearch-dashboards-2.0.0.yml] .
 * @param args.action <optional> - Action to be performed. Default is 'check'. Acceptable values are 'check' and 'create'.
 */
void call(Map args = [:]) {
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-infra-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-infra-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

    def secret_github_bot = [
        [envVar: 'GITHUB_USER', secretRef: 'op://opensearch-infra-secrets/github-bot/ci-bot-username'],
        [envVar: 'GITHUB_TOKEN', secretRef: 'op://opensearch-infra-secrets/github-bot/ci-bot-token']
    ]

    def inputManifest = args.inputManifest
    String action = args.action ?: 'check'

    // Parameter check
    if (!inputManifest || inputManifest.isEmpty()) {
        error "inputManifest parameter is required."
    }
    if (action != 'check' && action != 'create') {
        error "Invalid action '${action}'. Valid values: check, create"
    }

    def inputManifestYaml = readYaml(file: args.inputManifest[0])
    def version = inputManifestYaml.build.version
    List<String> componentsMissingReleaseIssue = []

    inputManifest.each { inputManifestFile ->
        def inputManifestObj = readYaml(file: inputManifestFile)
        withSecrets(secrets: secret_metrics_cluster){
            withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
                def metricsUrl = env.METRICS_HOST_URL
                def awsAccessKey = env.AWS_ACCESS_KEY_ID
                def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
                def awsSessionToken = env.AWS_SESSION_TOKEN

                ReleaseMetricsData releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)
                inputManifestObj.components.each { component ->
                    if (!releaseMetricsData.getReleaseIssueStatus(component.name)) {
                        componentsMissingReleaseIssue.add(component.name)
                    }
                }
            }
        }
    }
    echo("Components missing release issues: " + componentsMissingReleaseIssue)

    if (action == 'create' && !componentsMissingReleaseIssue.isEmpty()) {
        withSecrets(secrets: secret_github_bot){
            println("Triggering GitHub workflows for creating issues:")
            try {
                sh(
                    script: "gh workflow run os-release-issues.yml -R opensearch-project/opensearch-build && gh workflow run osd-release-issues.yml -R opensearch-project/opensearch-build",
                    returnStdout: true
                )
            } catch (Exception ex) {
                error("Error in triggering GitHub Actions workflows. ${ex.getMessage()}")
            }
        }
    }

}
