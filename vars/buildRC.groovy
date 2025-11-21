/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.ReleaseCandidateStatus

/** Library to build release candidate for OpenSearch and OpenSearch-Dashboards
 *  The library will fetch the latest RC number from metrics and increment it by 1.
 *  For manual RC number, please use the distribution-build-opensearch or distribution-build-opensearch-dashboards job directly
 *  @param Map args = [:] args A map of the following parameters
 *  @param args.version <required> - Release version along with qualifier eg:3.0.0-alpha1.
 *  @param args.product <optional> - Product name. eg: opensearch and/or opensearch-dashboards. Defaults to both
 **/

void call(Map args = [:]) {
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-infra-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-infra-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]
    validateParameters(args)
    def buildIndexName = 'opensearch-distribution-build-results'
    def versionTokenize = args.version.tokenize('-')
    def version = versionTokenize[0]
    def qualifier = versionTokenize[1] ?: "None"
    def opensearchRcNumber
    def opensearchDashboardsRcNumber

    withSecrets(secrets: secret_metrics_cluster){
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN

            ReleaseCandidateStatus releaseCandidateStatus = new ReleaseCandidateStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, version, qualifier, this)

            opensearchRcNumber = releaseCandidateStatus.getLatestRcNumber('OpenSearch')
            opensearchDashboardsRcNumber = releaseCandidateStatus.getLatestRcNumber('OpenSearch-Dashboards')
        }
    }

    echo("Retrieved Current RC numbers: OpenSearch - ${opensearchRcNumber}, OpenSearch-Dashboards - ${opensearchDashboardsRcNumber}")
    switch (args.product) {
        case ("opensearch"):
            if (opensearchRcNumber == null) {
                error("Unable to fetch latest RC number from metrics. Received null value.")
            } else {
                opensearchRcNumber = (opensearchRcNumber as Integer) + 1
                echo("Only triggering OpenSearch build with RC number: ${opensearchRcNumber}")
                triggerBuildWorkflow(args.version, 'opensearch', opensearchRcNumber.toString())
            }
            break
        case ("opensearch-dashboards"):
            if (opensearchDashboardsRcNumber == null) {
                error("Unable to fetch latest RC number from metrics. Received null value.")
            } else {
                opensearchDashboardsRcNumber = (opensearchDashboardsRcNumber as Integer) + 1
                echo("Only triggering OpenSearch-Dashboards build with RC number: ${opensearchDashboardsRcNumber}")
                triggerBuildWorkflow(args.version, 'opensearch-dashboards', opensearchDashboardsRcNumber.toString())
            }
            break
        case ("both"):
            if (opensearchRcNumber == null || opensearchDashboardsRcNumber == null) {
                error("Unable to fetch latest RC number from metrics. Received null value.")
            }
            else {
                opensearchRcNumber = (opensearchRcNumber as Integer) + 1
                opensearchDashboardsRcNumber = (opensearchDashboardsRcNumber as Integer) + 1
                echo("Triggering both OpenSearch and OpenSearch-Dashboards builds with RC numbers: ${opensearchRcNumber}, ${opensearchDashboardsRcNumber} respectively")
                parallel(
                    opensearch: {
                        triggerBuildWorkflow(args.version, 'opensearch', opensearchRcNumber.toString())
                    },
                    opensearchDashboards: {
                        triggerBuildWorkflow(args.version, 'opensearch-dashboards', opensearchDashboardsRcNumber.toString())
                    }
                )
            }
            break
        }
}

/**
 * Trigger build workflow for a specific product and required parameters for RC
 */
def triggerBuildWorkflow(String version, String product, String rcNumber) {
        build job: "distribution-build-${product}", 
        parameters: [
            string(name: 'INPUT_MANIFEST', value: "${version}/${product}-${version}.yml"),
            string(name: 'TEST_MANIFEST', value: "${version}/${product}-${version}-test.yml"),
            string(name: 'BUILD_PLATFORM', value: "linux windows"),
            string(name: 'BUILD_DISTRIBUTION', value: "tar rpm deb zip"),
            string(name: 'TEST_PLATFORM', value: "linux windows"),
            string(name: 'TEST_DISTRIBUTION', value: "tar rpm deb zip"),
            string(name: 'RC_NUMBER', value: "${rcNumber}"),
            string(name: 'BUILD_DOCKER', value: "build_docker_with_build_number_tag"),
            booleanParam(name: 'CONTINUE_ON_ERROR', value: false),
            booleanParam(name: 'UPDATE_GITHUB_ISSUE', value: true),
        ], 
        wait: false,
        propagate: false
}

/**
 * Validates input parameters
 */
private void validateParameters(Map args) {
    if (!args.version) {
        error "Version parameter is required."
    }
    if (isNullOrEmpty(args.product)) {
        args.product = 'both'
    }
    if (args.product) {
        List<String> validProducts = ['opensearch', 'opensearch-dashboards', 'both']
        if (!validProducts.contains(args.product.toString())) {
            error "Invalid product '${args.product}'. Valid values: ${validProducts.join(', ')}"
        }
    }
}

/**
 * Check if a string is null or empty
 */
private boolean isNullOrEmpty(String str) {
    return (str == 'Null' || str == null || str.allWhitespace || str.isEmpty()) || str == "None"
}