/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.ReleaseMetricsData
import jenkins.ComponentRepoData
import java.time.LocalDate
import utils.TemplateProcessor
/**
 * Library to check and assign release owners to the release issues.
 * @param Map args = [:] args A map of the following parameters
 * @param args.inputManifest <required> - Input manifest file(s) eg: [manifests/2.0.0/opensearch-2.0.0.yml, manifests/2.0.0/opensearch-dashboards-2.0.0.yml] .
 * @param args.action <optional> - Action to be performed. Default is 'check'. Acceptable values are 'check', 'request' and 'assign'.
 */
void call(Map args = [:]) {
    def inputManifest = args.inputManifest
    String action = args.action ?: 'check'

    // Parameter validation
    validateParameters(args)
    def manifestYaml = readYaml(file: inputManifest[0])
    def version = manifestYaml.build.version

    List<String> componentsMissingReleaseOwners = []

    inputManifest.each { inputManifestFile ->
        def inputManifestObj = readYaml(file: inputManifestFile)
        withCredentials([
                string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
                string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')]) {
            withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
                def metricsUrl = env.METRICS_HOST_URL
                def awsAccessKey = env.AWS_ACCESS_KEY_ID
                def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
                def awsSessionToken = env.AWS_SESSION_TOKEN

                ReleaseMetricsData releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)
                ComponentRepoData componentRepoData = new ComponentRepoData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, this)

                inputManifestObj.components.each { component ->
                    def releaseOwner = releaseMetricsData.getReleaseOwners(component.name)
                    if (releaseOwner == null || releaseOwner.isEmpty()) {
                        componentsMissingReleaseOwners.add(component.name)
                        if (action != 'check') {
                            handleMissingReleaseOwner(component, releaseMetricsData, componentRepoData, action)
                        }
                    }
                }
            }
        }
    }
    if (!componentsMissingReleaseOwners.isEmpty()) {
        echo("Components missing release owner: ${componentsMissingReleaseOwners}")
    } else {
        echo('All components have release owner assigned.')
    }
}

/**
 * Validates input parameters
 */
private void validateParameters(Map args) {
    if (!args.inputManifest || args.inputManifest.isEmpty()) {
        error "inputManifest parameter is required."
    } else {
        args.inputManifest.each { inputManifestFile ->
            if (!fileExists(inputManifestFile)) {
                error("Invalid path. Input manifest file does not exist at ${inputManifestFile}")
            }
        }
    }

    String action = args.action
    println("Action is: ${action}")
    List<String> validActions = ['check', 'assign', 'request']
    if (!validActions.contains(action)) {
        error "Invalid action '${action}'. Valid values: ${validActions.join(', ')}"
    }
}

/**
 * Handle component with missing release owner
 */
private void handleMissingReleaseOwner(def component, ReleaseMetricsData releaseMetricsData,  ComponentRepoData componentRepoData, String action) {
    def now = LocalDate.now()
    def monthYear = String.format("%02d-%d", now.monthValue, now.year)
    def maintainersIndex = "maintainer-inactivity-${monthYear}"
    String repoName = component.repository.toString().split('/')[-1].replace('.git', '')
    String releaseIssueUrl = releaseMetricsData.getReleaseIssue(repoName)
    ArrayList<String> componentMaintainers = componentRepoData.getMaintainers(repoName, maintainersIndex)

    if (componentMaintainers == null || componentMaintainers.isEmpty()) {
        echo("No maintainers found for component: ${component.name}. Skipping action.")
        return
    }

    List<String> maintainersWithTag = componentMaintainers.collect { "@$it" }

    switch (action) {
        case 'request':
            requestMaintainers(component.name, releaseIssueUrl, maintainersWithTag)
            break
        case 'assign':
            assignRandomMaintainer(component.name, releaseIssueUrl, maintainersWithTag)
            break
        default:
            error("Invalid action: ${action}")
    }
}

/**
 * Notify maintainers about missing release owner
 */
private void requestMaintainers(String componentName, String releaseIssueUrl, List<String> maintainersWithTag) {
    try {
        Map values = [LIST_OF_MAINTAINERS: maintainersWithTag.join(", ")]
        def githubCommentBody = new TemplateProcessor(this).process("release/request-release-owner-template.md", values, "${WORKSPACE}")
        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
            sh(
                    script: "gh issue comment ${releaseIssueUrl} --body-file ${githubCommentBody}",
                    returnStdout: true
            )
        }
    } catch (Exception e) {
        error("Failed to request maintainers for ${componentName}: ${e.getMessage()}")
    }
}

/**
 * Assign a random maintainer as release owner
 */
private void assignRandomMaintainer(String componentName, String releaseIssueUrl, List<String> maintainersWithTag) {
    try {
        def random = new Random()
        def randomReleaseOwner = maintainersWithTag[random.nextInt(maintainersWithTag.size())]
        def randomReleaseOwnerFormatted = randomReleaseOwner.replace("@", '')
        Map values = [RELEASE_OWNER: randomReleaseOwner]
        def githubCommentBody = new TemplateProcessor(this).process("release/release-owner-assignment-template.md", values, "${WORKSPACE}")
        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
            sh(
                    script: "gh issue comment ${releaseIssueUrl} --body-file ${githubCommentBody}",
                    returnStdout: true
            )
            sh(
                    script: "gh issue edit ${releaseIssueUrl} --add-assignee ${randomReleaseOwnerFormatted}",
                    returnStdout: true
            )
        }
    } catch (Exception e) {
        error("Failed to assign release owner for ${componentName}: ${e.getMessage()}")
    }
}
