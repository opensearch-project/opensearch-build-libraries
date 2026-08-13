/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.ReleaseStateData
import jenkins.ReleaseCriterion

/**
 * Computes and indexes per-criterion release readiness state for active releases.
 *
 * For each active release (from the opensearch_release_schedule index), this runs the automated
 * release chore checks, maps each result to a criterion status, and indexes one criterion document
 * per check into the opensearch_release_state index. OSCAR reads that index to compute a Red/Yellow/
 * Green verdict; this job only records state (Jenkins is the hands, OSCAR is the brain).
 *
 * Each chore returns its problems (an empty result means the criterion is met, a non-empty result
 * means it is not met, and a null return from a query failure is recorded as 'unknown' so a failed
 * check never reads as met). A chore may return a flat list of blocking components, or a keyed map
 * (e.g. project -> CVEs, or dist/arch -> failing components); a per-check render closure normalizes
 * the latter into blocking_components plus a human-readable details breakdown.
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <optional> - Restrict to a single release version. When omitted, all active
 *                                   releases from the schedule index are processed.
 */
void call(Map args = [:]) {
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

            def releaseStateData = new ReleaseStateData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this)

            def releases = releaseStateData.getActiveReleases()
            if (args.version) {
                releases = releases.findAll { it.version == args.version }
            }
            if (releases.isEmpty()) {
                echo('No active releases to index state for.')
                return
            }

            releases.each { release ->
                echo("Indexing release state for version ${release.version}.")
                indexCriteriaForRelease(releaseStateData, release)
                indexManualCriteriaForRelease(releaseStateData, release)
            }
        }
    }
}

/**
 * Runs every automated criterion check for a release and indexes a criterion document for each.
 */
private void indexCriteriaForRelease(ReleaseStateData releaseStateData, Map release) {
    String version = release.version
    List<String> inputManifest = [
        "manifests/${version}/opensearch-${version}.yml",
        "manifests/${version}/opensearch-dashboards-${version}.yml"
    ]

    // Each entry maps a release criterion (from the release issue's entrance/exit tables) to the chore
    // that verifies it:
    //   name    - the criterion name, matching the release_state index
    //   type    - entrance or exit criterion
    //   product - which product line the criterion applies to
    //   run     - closure that runs the chore and returns its problems; invoked via runCheck so a
    //             failure in one check is isolated and recorded as 'unknown'
    //   render  - optional closure that normalizes a keyed-map result into blocking_components + details
    //
    // Criteria not covered by an automated chore (sanity testing, roadmap, security reviews,
    // performance tests, release blog) are manual and parsed from the release issue's tables by
    // indexManualCriteriaForRelease.
    def checks = [
        [
            name   : 'release_owners_assigned',
            type   : 'entrance',
            product: 'both',
            run    : { checkRequestAssignReleaseOwners(inputManifest: inputManifest, action: 'check') }
        ],
        [
            name   : 'documentation_draft_prs_up',
            type   : 'entrance',
            product: 'both',
            run    : { checkDocumentationIssues(version: version, action: 'check') }
        ],
        [
            name   : 'code_coverage_not_decreased',
            type   : 'entrance',
            product: 'both',
            run    : { checkCodeCoverage(inputManifest: inputManifest, action: 'check') }
        ],
        [
            name   : 'release_notes_ready',
            type   : 'entrance',
            product: 'both',
            run    : { checkReleaseNotes(inputManifest: inputManifest, action: 'check') }
        ],
        [
            name   : 'release_ticket_and_forum_post',
            type   : 'entrance',
            product: 'both',
            run    : { checkReleaseIssues(inputManifest: inputManifest, action: 'check') }
        ],
        [
            name   : 'documentation_reviewed_signed_off',
            type   : 'exit',
            product: 'both',
            run    : { checkDocumentationPullRequests(version: version) }
        ],
        [
            name   : 'all_integration_tests_passing',
            type   : 'exit',
            product: 'both',
            run    : { checkIntegTestResultsOverview(inputManifest: inputManifest) },
            render : { raw -> renderIntegResults(raw) }
        ],
        [
            name   : 'no_unpatched_vulnerabilities',
            type   : 'exit',
            product: 'both',
            run    : { checkUnpatchedVulnerabilities(version: version, releaseDate: release.releaseDate) },
            render : { raw -> renderVulnerabilityResults(raw) }
        ]
    ]

    checks.each { check ->
        def raw = runCheck(check.name, check.run)
        def result = normalizeResult(check, raw)
        indexCriterion(releaseStateData, release, check, result)
    }
}

/**
 * Reads the manual criteria (no chore verifies them) from the release issue's criteria tables and
 * indexes one criterion document for each. The issue body is fetched with the github-bot token and
 * parsed by ReleaseStateData; each row's status circle is recorded as-is (source 'issue_table').
 */
private void indexManualCriteriaForRelease(ReleaseStateData releaseStateData, Map release) {
    if (!release.releaseIssue) {
        echo("No release issue for version ${release.version}; skipping manual criteria.")
        return
    }

    def secret_github_bot = [
        [envVar: 'GITHUB_USER', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-username'],
        [envVar: 'GITHUB_TOKEN', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-token']
    ]

    String issueBody
    withSecrets(secrets: secret_github_bot) {
        issueBody = sh(
            script: "gh issue view ${release.releaseIssue} --repo opensearch-project/opensearch-build --json body --jq '.body'",
            returnStdout: true
        )
    }

    releaseStateData.parseManualCriteria(issueBody).each { criterion ->
        releaseStateData.indexCriterion(new ReleaseCriterion([
            version      : release.version,
            releaseDate  : release.releaseDate,
            product      : criterion.product,
            criterionType: criterion.type,
            criterionName: criterion.name,
            status       : criterion.status,
            source       : 'issue_table',
            releaseIssue : release.releaseIssue,
            checkedBy    : "${env.JOB_NAME} #${env.BUILD_NUMBER}"
        ]))
    }
}

/**
 * Renders a keyed problem map into a readable one-line summary for the criterion's details field,
 * e.g. [SQL: [CVE-1, CVE-2], Alerting: [CVE-3]] -> "SQL: CVE-1, CVE-2; Alerting: CVE-3".
 */
private String renderDetails(Map<String, List<String>> problemsByKey) {
    return problemsByKey.collect { key, items -> "${key}: ${items.join(', ')}" }.join('; ')
}

/**
 * checkUnpatchedVulnerabilities returns a Map of project -> blocking CVE ids. The projects are the
 * blocking components; the per-project CVE breakdown is preserved in details.
 */
private Map renderVulnerabilityResults(Map<String, List<String>> byProject) {
    return [blockingComponents: byProject.keySet().toList(), details: renderDetails(byProject)]
}

/**
 * checkIntegTestResultsOverview always returns a Map keyed by every "${dist}_${arch}", with an empty
 * list for combinations where nothing failed. The failing components (deduped across arch/dist) are
 * the blocking components; only the combinations that actually had failures are kept in details.
 */
private Map renderIntegResults(Map<String, List<String>> byDistArch) {
    Map<String, List<String>> failing = byDistArch.findAll { distArch, components -> components }
    List<String> components = failing.values().flatten().unique()
    return [blockingComponents: components, details: failing ? renderDetails(failing) : null]
}

/**
 * Invokes a chore check, returning its raw result, or null if the check threw so it is recorded
 * as 'unknown' rather than silently passing.
 */
private def runCheck(String name, Closure check) {
    try {
        return check.call()
    } catch (Exception e) {
        echo("Check '${name}' failed to run: ${e.getMessage()}. Recording status as unknown.")
        return null
    }
}

/**
 * Normalizes a check's raw result into an explicit [blockingComponents, details] contract so
 * indexCriterion never has to infer which axis of a Map is the component.
 *   - null (the check threw)      -> null, recorded as 'unknown'
 *   - checks with a render closure -> that closure derives components vs details
 *   - otherwise (a List<String>)   -> the list is the blocking components, no details
 *
 * "Met" is decided downstream from blockingComponents being empty, not from the raw container being
 * empty: checkIntegTestResultsOverview always returns a Map keyed by every arch/dist, with empty
 * lists when nothing fails, so the container is never empty even when the criterion is met.
 */
private Map normalizeResult(Map check, def raw) {
    if (raw == null) {
        return null
    }
    if (check.render) {
        return check.render(raw)
    }
    return [blockingComponents: raw, details: null]
}

/**
 * Builds and indexes a single criterion document from a normalized check result.
 * null -> unknown, empty blockingComponents -> met, otherwise not_met with the components and details.
 */
private void indexCriterion(ReleaseStateData releaseStateData, Map release, Map check, Map result) {
    String status
    List<String> blockingComponents = []
    String details = null
    if (result == null) {
        status = 'unknown'
    } else if (result.blockingComponents.isEmpty()) {
        status = 'met'
    } else {
        status = 'not_met'
        blockingComponents = result.blockingComponents
        details = result.details
    }

    releaseStateData.indexCriterion(new ReleaseCriterion([
        version            : release.version,
        releaseDate        : release.releaseDate,
        product            : check.product,
        criterionType      : check.type,
        criterionName      : check.name,
        status             : status,
        details            : details,
        blockingComponents : blockingComponents,
        source             : 'chore_check',
        releaseIssue       : release.releaseIssue,
        checkedBy          : "${env.JOB_NAME} #${env.BUILD_NUMBER}"
    ]))
}
