/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import jenkins.SecurityAdvisoryData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Checks the release exit criterion: "No unpatched vulnerabilities of medium or higher severity
 * that have been publicly known for more than 60 days."
 *
 * Reads the security advisories cluster (separate from the metrics cluster, with its own endpoint
 * and credentials) in three steps: resolve the latest scans index, collect the open (non-excluded)
 * CVE ids scanned for the release branch, then keep only those the advisories index reports as
 * medium/high/critical AND published on or before the age cutoff.
 *
 * Age cutoff is release_date - 60 days when a release date is known, so a vulnerability is blocking
 * only if it will have been public for more than 60 days by the time the release ships. When no
 * release date is supplied, it falls back to now() - 60 days.
 *
 * The version is resolved to a branch tag (origin/3.8, origin/main) — never the exact 3.8.0 tag,
 * which only exists after release.
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <required> - Release version, e.g. "3.8.0" or "3.8" (resolved to origin/3.8).
 * @param args.releaseDate <optional> - Release date as yyyy-MM-dd; the age window is measured back
 *                                       from this date. Falls back to today when omitted.
 * @return Map of project name -> list of its blocking CVE ids (empty map when the criterion is met).
 *         A bare CVE list is not actionable, so blocking CVEs are keyed by the component that owns
 *         them. A query failure throws, so the caller records the criterion as 'unknown' rather than
 *         silently passing.
 */
Map<String, List<String>> call(Map args = [:]) {
    if (!args.version) {
        error('version parameter is required.')
    }

    def secret_advisories_cluster = [
        [envVar: 'ADVISORIES_HOST_ACCOUNT', secretRef: 'op://opensearch-release-secrets/aws-accounts/security-advisories-account-number'],
        [envVar: 'ADVISORIES_HOST_URL', secretRef: 'op://opensearch-release-secrets/security-advisories-cluster/security-advisories-cluster-endpoint']
    ]

    String branchTag = SecurityAdvisoryData.resolveVersionTag(args.version)
    String cutoffIso = ageCutoffIso(args.releaseDate)
    echo("Checking unpatched medium-or-higher vulnerabilities for ${branchTag} (published on or before ${cutoffIso}).")

    Map<String, List<String>> blockingByProject = [:]
    withSecrets(secrets: secret_advisories_cluster) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${ADVISORIES_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def advisoriesUrl = env.ADVISORIES_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN

            def advisoryData = new SecurityAdvisoryData(advisoriesUrl, awsAccessKey, awsSecretKey, awsSessionToken, this)

            String scansIndex = advisoryData.getLatestScansIndex()
            Map<String, List<String>> openByProject = advisoryData.getOpenVulnerabilitiesByProject(scansIndex, branchTag)
            List<String> openCves = openByProject.values().flatten().unique()
            echo("Found ${openCves.size()} open vulnerability id(s) across ${openByProject.size()} project(s) for ${branchTag} in ${scansIndex}.")

            // The advisories lookup is severity/age filtering over the whole CVE set; re-key the
            // resulting blocking ids back onto their projects so each is attributable to a component.
            List<String> blockingCves = advisoryData.getAgedMediumOrHigherAdvisories(openCves, cutoffIso)
            Set<String> blockingCveSet = blockingCves as Set
            openByProject.each { projectName, cves ->
                def projectBlocking = cves.findAll { blockingCveSet.contains(it) }
                if (projectBlocking) {
                    blockingByProject[projectName] = projectBlocking
                }
            }
        }
    }

    if (blockingByProject) {
        echo("Unpatched medium-or-higher vulnerabilities older than the 60-day window: ${blockingByProject}")
    } else {
        echo('No unpatched medium-or-higher vulnerabilities older than the 60-day window.')
    }
    return blockingByProject
}

/**
 * Age cutoff for "publicly known for more than 60 days": release_date - 60 days when a valid release
 * date is supplied, otherwise today - 60 days. The schedule stores a date (yyyy-MM-dd) while
 * advisories carry a datetime (timestamp.publish); rendering the cutoff at end-of-day
 * (T23:59:59.999Z) means an advisory published anytime on the cutoff date is still treated as aged.
 */
private String ageCutoffIso(String releaseDate) {
    LocalDate base
    if (releaseDate?.trim()) {
        try {
            base = LocalDate.parse(releaseDate.trim(), DateTimeFormatter.ofPattern('yyyy-MM-dd'))
        } catch (Exception e) {
            echo("Could not parse releaseDate '${releaseDate}'; falling back to today for the age window.")
            base = LocalDate.now()
        }
    } else {
        base = LocalDate.now()
    }
    return "${base.minusDays(60).format(DateTimeFormatter.ofPattern('yyyy-MM-dd'))}T23:59:59.999Z"
}
