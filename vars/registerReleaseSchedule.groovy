/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.ReleaseStateData
import jenkins.ReleaseSchedule

/**
 * Registers a release schedule document in the opensearch_release_schedule index.
 * Run once per release to record its RC and release dates so downstream jobs and OSCAR
 * can discover active releases and compute notification cadence.
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <required> - Release version. eg: 3.8.0
 * @param args.rcDate <optional> - Release candidate date in yyyy-MM-dd. eg: 2026-08-01
 * @param args.releaseDate <optional> - Release date in yyyy-MM-dd. eg: 2026-08-12
 * @param args.releaseIssue <optional> - URL of the GitHub release issue.
 * @param args.releaseManager <optional> - GitHub handle of the release manager.
 * @param args.status <optional> - Schedule status. Defaults to 'inactive'. One of: inactive, active, released, cancelled.
 */
void call(Map args = [:]) {
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

    def schedule = new ReleaseSchedule([
        version       : args.version,
        rcDate        : args.rcDate,
        releaseDate   : args.releaseDate,
        releaseIssue  : args.releaseIssue,
        releaseManager: args.releaseManager,
        status        : args.status,
        registeredBy  : "${env.JOB_NAME} #${env.BUILD_NUMBER}"
    ])

    withSecrets(secrets: secret_metrics_cluster) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN

            def releaseStateData = new ReleaseStateData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this)
            releaseStateData.registerSchedule(schedule)
            echo("Registered release schedule for version ${args.version} with status '${schedule.status}'.")
        }
    }
}
