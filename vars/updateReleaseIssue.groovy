/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.ReleaseStateData
import jenkins.ReleaseCriterionCatalog

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

    def issueNumber = (releaseIssue =~ /\/issues\/(\d+)/)
    if (!issueNumber.find()) {
        error("Release issue '${releaseIssue}' is not a valid GitHub issue URL.")
    }
    String issueRef = issueNumber.group(1)

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

    Map<String, String> statuses = readChoreStatuses(version)
    if (statuses.isEmpty()) {
        echo("No chore-verified statuses indexed for version ${version}; nothing to write back.")
        return
    }

    withSecrets(secrets: secret_github_oscar_bot) {
        String issueBody = sh(
            script: "gh issue view ${issueRef} --repo opensearch-project/opensearch-build --json body --jq '.body'",
            returnStdout: true
        )
        String updatedBody = applyChoreCircles(issueBody, statuses)
        if (updatedBody == issueBody) {
            echo("Release issue ${issueRef} circles already match the indexed statuses; no edit needed.")
            return
        }
        writeFile(file: 'release-issue-body.md', text: updatedBody)
        sh(script: "gh issue edit ${issueRef} --repo opensearch-project/opensearch-build --body-file release-issue-body.md")
    }
}

/**
 * Maps a criterion status to the status circle written back to the issue table. Only these three
 * states have a circle; any other status (e.g. unknown) returns null and leaves the row untouched.
 */
private String statusCircle(String status) {
    return [
        met        : ':green_circle:',
        in_progress: ':yellow_circle:',
        not_met    : ':red_circle:'
    ][status]
}

private Map<String, String> readChoreStatuses(String version) {
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]
    Map<String, String> statuses = [:]
    withSecrets(secrets: secret_metrics_cluster) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def releaseStateData = new ReleaseStateData(env.METRICS_HOST_URL, env.AWS_ACCESS_KEY_ID, env.AWS_SECRET_ACCESS_KEY, env.AWS_SESSION_TOKEN, this)
            statuses = releaseStateData.getLatestChoreStatuses(version)
        }
    }
    return statuses
}

/**
 * Rewrites the status circle of each chore-verified criterion row to reflect its indexed status.
 * A row is matched by locating a chore criterion's keyword in the Criteria cell; only its status
 * cell (the first circle on the line) is replaced, leaving the other columns untouched. Rows whose
 * status is unknown, or has no circle mapping, are left as-is so a transient failure never blanks a
 * circle the release manager can see.
 */
private String applyChoreCircles(String issueBody, Map<String, String> statuses) {
    def choreByKeyword = ReleaseCriterionCatalog.choreCriteria()
    // Split after each newline so line terminators are preserved and an unchanged body round-trips.
    return issueBody.split(/(?<=\n)/).collect { line ->
        if (!line.contains('|')) {
            return line
        }
        String criteriaCell = line.split('\\|')[0].toLowerCase()
        def criterion = choreByKeyword.find { criteriaCell.contains(it.keyword) }
        if (!criterion) {
            return line
        }
        String circle = statusCircle(statuses[criterion.criterionName])
        if (!circle) {
            return line
        }
        return line.replaceFirst(/:(green|yellow|red)_circle:/, java.util.regex.Matcher.quoteReplacement(circle))
    }.join('')
}
