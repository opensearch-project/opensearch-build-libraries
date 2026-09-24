/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import com.cloudbees.groovy.cps.NonCPS
import jenkins.ReleaseStateData
import jenkins.ReleaseDecision

/**
 * Records a release manager's Go/No-Go decision for a version.
 *
 * The decision is the one part of release readiness a machine does not get to make: OSCAR only ever
 * recommends, and this captures what the human concluded, alongside a snapshot of the criteria they
 * concluded it from. Indexing the recommendation next to the decision is what makes it possible to
 * ask later whether OSCAR was worth listening to.
 *
 * Two writes, in this order: the decision document into opensearch_release_state, then a comment on
 * the release issue so the decision is visible where the release is actually tracked. The index
 * write comes first because it is the durable record - a failed comment leaves the decision captured,
 * whereas a comment without the document would be a decision nothing can query.
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <required> - Release version the decision applies to, e.g. 3.8.0.
 * @param args.decision <required> - The decision: 'go', 'no-go' or 'hold'.
 * @param args.decidedBy <required> - The authenticated Slack user id of whoever decided.
 * @param args.decidedByDisplayName <optional> - A readable name for them, recorded and shown beside the
 *                                                id. Unverified, so it never replaces the id.
 * @param args.releaseIssue <optional> - Full GitHub issue URL. Resolved from the schedule index when
 *                                        omitted; no comment is posted if it cannot be resolved.
 * @param args.oscarRecommendation <optional> - OSCAR's verdict at decision time: 'red', 'yellow' or 'green'.
 * @param args.notes <optional> - Free-text rationale, e.g. why a criterion was waived.
 * @return the indexed decision document.
 */
Map call(Map args = [:]) {
    if (!args.version || !args.decision || !args.decidedBy) {
        error('version, decision and decidedBy parameters are required.')
    }

    String version = args.version
    def secret_metrics_cluster = [
        [envVar: 'METRICS_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/jenkins-health-metrics-account-number'],
        [envVar: 'METRICS_HOST_URL', secretRef: 'op://opensearch-release-secrets/metrics-cluster/jenkins-health-metrics-cluster-endpoint']
    ]

    Map decisionDocument = null
    String releaseIssue = args.releaseIssue
    withSecrets(secrets: secret_metrics_cluster) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def releaseStateData = new ReleaseStateData(env.METRICS_HOST_URL, env.AWS_ACCESS_KEY_ID, env.AWS_SECRET_ACCESS_KEY, env.AWS_SESSION_TOKEN, this)

            if (!releaseIssue) {
                def scheduled = releaseStateData.getActiveReleases().find { it.version == version }
                releaseIssue = scheduled?.releaseIssue
            }

            Map criteriaSnapshot = releaseStateData.getLatestCriteriaStatuses(version)
            echo("Recording '${args.decision}' for version ${version} against ${criteriaSnapshot.size()} criterion snapshot(s).")

            def decision = new ReleaseDecision([
                version             : version,
                decidedBy           : args.decidedBy,
                decidedByDisplayName: args.decidedByDisplayName,
                decision            : args.decision,
                oscarRecommendation : args.oscarRecommendation,
                agreedWithOscar     : agreedWithOscar(args.decision, args.oscarRecommendation),
                criteriaSnapshot    : criteriaSnapshot,
                releaseIssue        : releaseIssue,
                notes               : args.notes
            ])
            releaseStateData.indexDecision(decision)
            decisionDocument = decision.toDocument(null)
        }
    }

    if (releaseIssue) {
        updateReleaseIssue(version: version, releaseIssue: releaseIssue, action: 'comment',
            comment: decisionComment(version, args, decisionDocument.agreed_with_oscar,
                decisionDocument.criteria_snapshot))
    } else {
        echo("No release issue for version ${version}; the decision was indexed but not commented.")
    }
    return decisionDocument
}

/**
 * Whether the decision matched what OSCAR recommended: a 'go' agrees with green, and anything else
 * agrees with red or yellow. Null when there was no recommendation to compare against, which is not
 * the same as disagreeing - a decision taken without one must not read as overriding OSCAR.
 */
@NonCPS
private static Boolean agreedWithOscar(String decision, String recommendation) {
    if (!recommendation) {
        return null
    }
    return (decision == 'go') == (recommendation == 'green')
}

/**
 * The decision comment posted to the release issue. It restates the criteria the decision was taken
 * against, so a reader can see what was outstanding at the time without querying the index - which
 * matters most for a decision that went against the recommendation.
 */
private static String decisionComment(String version, Map args, Boolean agreed, Map criteriaSnapshot) {
    String heading = ['go': 'Go', 'no-go': 'No-Go', 'hold': 'Hold'].get(args.decision, args.decision)
    List lines = ["## Release decision: ${heading}", '', "**Version:** ${version}",
                  "**Decided by:** ${decidedByLabel(args)}"]
    if (args.oscarRecommendation) {
        String verdict = args.oscarRecommendation.toUpperCase()
        lines.add("**OSCAR recommendation:** ${verdict}" + (agreed == false ? ' (decision differs from the recommendation)' : ''))
    }
    if (args.notes) {
        lines.addAll(['', "**Notes:** ${args.notes}"])
    }
    lines.addAll(criteriaTable(criteriaSnapshot))
    lines.addAll(['', '_The release manager owns this decision; OSCAR is advisory only._'])
    return lines.join('\n')
}

/**
 * How the decider is named. The Slack id is the authenticated identity but means nothing to a reader,
 * so a supplied display name leads and the id stays alongside it - the id is the part that is proven,
 * and dropping it would leave the record resting on an unverified label.
 */
private static String decidedByLabel(Map args) {
    String displayName = args.decidedByDisplayName?.toString()?.trim()
    return displayName ? "${displayName} (Slack ${args.decidedBy})" : "Slack ${args.decidedBy}"
}

/**
 * Every criterion and its status at decision time, as a table. Criteria that apply per product are
 * listed once per product, since a release can be blocked on one product only.
 */
private static List criteriaTable(Map criteriaSnapshot) {
    if (!criteriaSnapshot) {
        return ['', '_No criteria state was indexed for this version at the time of the decision._']
    }
    List rows = []
    for (criterion in criteriaSnapshot.keySet().sort()) {
        Map byProduct = criteriaSnapshot[criterion]
        for (product in byProduct.keySet().sort()) {
            Map entry = byProduct[product]
            String circle = ReleaseStateData.statusCircle(entry?.status) ?: ':white_circle:'
            List blocking = entry?.blockingComponents ?: []
            String blockingCell = blocking ? blocking.join(', ') : '-'
            rows.add("| `${criterion}` | ${product} | ${circle} ${entry?.status ?: 'unknown'} | ${blockingCell} |")
        }
    }
    return ['', '### Criteria at the time of this decision', '',
            '| Criterion | Product | Status | Blocking |', '| -- | -- | -- | -- |'] + rows
}
