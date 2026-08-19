/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.ReleaseStateData

/**
 * Writes release readiness back to the GitHub release issue.
 *
 * Two independent actions, selected by args.action:
 *   - 'update_criteria' (default): reads the latest chore-verified status per criterion from the
 *     opensearch_release_state index and rewrites the matching status circle in the issue's criteria
 *     tables. Only chore-verified rows are touched; the manual rows stay under the release manager's
 *     control. OSCAR computes verdicts from the index, so this write-back only mirrors state for a
 *     human reading the issue.
 *   - 'comment': posts args.comment to the issue (used for OSCAR recommendations / decision capture).
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <required> - Release version, e.g. 3.8.0.
 * @param args.releaseIssue <required> - Full GitHub issue URL from the schedule index.
 * @param args.action <optional> - 'update_criteria' (default) or 'comment'.
 * @param args.comment <optional> - Markdown body to post; required when action is 'comment'.
 */
void call(Map args = [:]) {
    String version = args.version
    String releaseIssue = args.releaseIssue
    String action = args.action ?: 'update_criteria'
    if (!version || !releaseIssue) {
        error('version and releaseIssue are required.')
    }

    // Validate and extract the issue number without keeping a Matcher in a pipeline local: ==~ yields
    // a boolean and replaceAll returns a String, so nothing non-serializable survives a CPS step.
    String issuePattern = /^https:\/\/github\.com\/opensearch-project\/opensearch-build\/issues\/\d+$/
    if (!(releaseIssue ==~ issuePattern)) {
        error("Release issue '${releaseIssue}' is not a valid opensearch-build issue URL.")
    }
    String issueRef = releaseIssue.replaceAll(/^.*\/issues\//, '')

    // The oscar bot has write access to the release issue (edit body and comment).
    def secret_github_oscar_bot = [
        [envVar: 'GITHUB_USER', secretRef: 'op://opensearch-release-secrets/github-bot/oscar-ci-bot-username'],
        [envVar: 'GITHUB_TOKEN', secretRef: 'op://opensearch-release-secrets/github-bot/oscar-ci-bot-token']
    ]

    if (action == 'comment') {
        if (!args.comment) {
            error("comment is required when action is 'comment'.")
        }
        withSecrets(secrets: secret_github_oscar_bot) {
            writeFile(file: 'release-issue-comment.md', text: args.comment)
            sh(script: "gh issue comment ${issueRef} --repo opensearch-project/opensearch-build --body-file release-issue-comment.md")
        }
        return
    }
    if (action != 'update_criteria') {
        error("Invalid action '${action}'. Valid values: update_criteria, comment")
    }

    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

    // Build the data helper and read statuses under the metrics-cluster credentials. The same instance
    // rewrites the issue body later (applyChoreStatusCircles is pure), so it is held across both blocks.
    def releaseStateData = null
    Map<String, Map<String, String>> statuses = [:]
    withSecrets(secrets: secret_metrics_cluster) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            releaseStateData = new ReleaseStateData(env.METRICS_HOST_URL, env.AWS_ACCESS_KEY_ID, env.AWS_SECRET_ACCESS_KEY, env.AWS_SESSION_TOKEN, this)
            statuses = releaseStateData.getLatestChoreStatuses(version)
        }
    }
    if (statuses.isEmpty()) {
        echo("No chore-verified statuses indexed for version ${version}; nothing to write back.")
        return
    }

    withSecrets(secrets: secret_github_oscar_bot) {
        String issueBody = sh(
            script: "gh issue view ${issueRef} --repo opensearch-project/opensearch-build --json body --jq '.body'",
            returnStdout: true
        ).replaceAll(/\n$/, '')
        String updatedBody = releaseStateData.applyChoreStatusCircles(issueBody, statuses)
        if (updatedBody == issueBody) {
            echo("Release issue ${issueRef} circles already match the indexed statuses; no edit needed.")
            return
        }
        writeFile(file: 'release-issue-body.md', text: updatedBody)
        sh(script: "gh issue edit ${issueRef} --repo opensearch-project/opensearch-build --body-file release-issue-body.md")
    }
}

