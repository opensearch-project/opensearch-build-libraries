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
 * @return Map of product ('opensearch' / 'opensearch-dashboards') to a map of "${distribution}_${architecture}"
 *         to the list of components failing integration tests (empty lists when all pass).
 */
Map<String, Map> call(Map args = [:]) {
    lib = library(identifier: 'jenkins@13.8.1', retriever: legacySCM(scm))

    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

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

    def failingComponents = ['opensearch': [:], 'opensearch-dashboards': [:]]

    withSecrets(secrets: secret_metrics_cluster){
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN
            ComponentIntegTestStatus componentIntegTestStatus = new ComponentIntegTestStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, 'opensearch-integration-test-results', version, qualifier, this)
            ReleaseCandidateStatus releaseCandidateStatus = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, 'opensearch-distribution-build-results', version, qualifier, this)
            def opensearchRcNumber = releaseCandidateStatus.getLatestRcNumber('OpenSearch')
            def opensearchDashboardsRcNumber = releaseCandidateStatus.getLatestRcNumber('OpenSearch-Dashboards')

            // getLatestRcNumber returns null only on a query failure, and 0 when no successful RC
            // build exists yet (the normal pre-RC state, before the RC date). A null is a real error;
            // a 0 is not — before the first RC, fall back to the current non-RC integration results
            // (rc_number 0) so the criterion reflects live test status instead of reading as unknown
            // for the whole pre-RC window.
            if (opensearchRcNumber == null || opensearchDashboardsRcNumber == null) {
                    error("Unable to fetch latest RC number from metrics. Received null value.")
            } else {
                if (opensearchRcNumber == 0 || opensearchDashboardsRcNumber == 0) {
                    echo("No successful RC build yet; reporting current (non-RC) integration test results.")
                }
                archDistMap.each {arch, distributions ->
                    distributions.each { dist ->
                        failingComponents['opensearch']["${dist}_${arch}"] = componentIntegTestStatus.getAllFailedComponents(opensearchRcNumber, dist, arch, openSearchComponents)
                        failingComponents['opensearch-dashboards']["${dist}_${arch}"] = componentIntegTestStatus.getAllFailedComponents(opensearchDashboardsRcNumber, dist, arch, openSearchDashboardsComponents)
                    }
                }
                def formattedOutput = failingComponents.collect { product, byDistArch ->
                    "${product}:\n" + byDistArch.collect { key, value -> "  ${key}: ${value}" }.join('\n')
                }.join('\n')
                echo "Components failing integration tests:\n${formattedOutput}"
            }
        }
    }

    return failingComponents
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
