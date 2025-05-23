/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.ComponentIntegTestStatus
import jenkins.ReleaseCandidateStatus
/**
 * Library to check integration test results for all components, platforms, architectures and distribution issues per release in html view.
 * Attaches an html document in the Jenkins job with the results.
 * @param Map args = [:] args A map of the following parameters
 * @param args.inputManifest <required> - Input manifest file(s) eg: [manifests/2.0.0/opensearch-2.0.0.yml, manifests/2.0.0/opensearch-dashboards-2.0.0.yml] .
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@9.2.0', retriever: legacySCM(scm))
    // Parameter validation
    validateParameters(args)
    def inputManifest = args.inputManifest
    List<String> openSearchComponents = []
    List<String> openSearchDashboardsComponents = []

    inputManifest.each { inputManifestFile ->
        def inputManifestObj = lib.jenkins.InputManifest.new(readYaml(file: inputManifestFile))
        if (inputManifestObj.build.getFilename() == 'opensearch') {
            openSearchComponents.addAll(inputManifestObj.getNames())
        } else {
            openSearchDashboardsComponents.addAll(inputManifestObj.getNames())
        }
    }

    def manifestYaml = readYaml(file: inputManifest[0])
    def version = manifestYaml.build.version
    def qualifier = "None"
    if (manifestYaml.build.qualifier) {
        qualifier = manifestYaml.build.qualifier
    }

    Map<String, List> archDistMap = [
            "x64": ['tar', 'rpm', 'deb', 'zip'],
            "arm64": ['tar', 'rpm', 'deb']
    ]

    def failingComponents = [:]

    withCredentials([
            string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
            string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')]) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN
            ComponentIntegTestStatus componentIntegTestStatus = new ComponentIntegTestStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, 'opensearch-integration-test-results', version, qualifier, this)
            ReleaseCandidateStatus releaseCandidateStatus = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, 'opensearch-distribution-build-results', version, qualifier, this)
            def opensearchRcNumber = releaseCandidateStatus.getLatestRcNumber('OpenSearch')
            def opensearchDashboardsRcNumber = releaseCandidateStatus.getLatestRcNumber('OpenSearch-Dashboards')

            archDistMap.each {arch, distributions ->
                distributions.each { dist ->
                    def osFailedComponents = componentIntegTestStatus.getAllFailedComponents(opensearchRcNumber, dist, arch, openSearchComponents)
                    def osdFailedComponents = componentIntegTestStatus.getAllFailedComponents(opensearchDashboardsRcNumber, dist, arch, openSearchDashboardsComponents)
                    failingComponents["${dist}_${arch}"] = osFailedComponents + osdFailedComponents
                }
            }
        }
    }
    def formattedOutput = failingComponents.collect { key, value ->
        "${key}: ${value}"
    }.join('\n')
    echo "Components failing integration tests:\n${formattedOutput}"
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
}
