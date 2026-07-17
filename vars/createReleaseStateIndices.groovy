/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.ReleaseStateIndex

/**
 * Creates the release state indices (opensearch_release_schedule, opensearch_release_state)
 * on the OpenSearch metrics cluster if they don't already exist.
 */
void call() {
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

    withSecrets(secrets: secret_metrics_cluster) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN

            def releaseStateIndex = new ReleaseStateIndex(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this)
            releaseStateIndex.createIndicesIfNotExist()
        }
    }
}
