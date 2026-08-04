/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import groovy.json.JsonOutput
import utils.SecurityAdvisoriesQuery

/**
 * Reads unpatched-vulnerability data from the security advisories OpenSearch cluster.
 *
 * Mirrors ReleaseStateData in shape (a thin, testable wrapper over a query client), but reads a
 * different, read-only cluster. The data supports the release exit criterion "no unpatched
 * vulnerabilities of medium or higher severity that have been publicly known for more than 60 days":
 *
 *   1. resolveVersionTag  - map a release version to the branch tag the scans are keyed on
 *                           (e.g. 3.8.0 / 3.8 -> origin/3.8, main -> origin/main). Never the exact
 *                           3.8.0 tag, which only exists after release.
 *   2. getLatestScansIndex - resolve the newest concrete scans-NNNNNN index.
 *   3. getOpenVulnerabilityIds - the open (non-excluded) CVE ids scanned for that branch tag.
 *   4. getAgedMediumOrHigherAdvisories - of those ids, the ones the advisories index says are
 *                           medium/high/critical AND were published on or before the cutoff.
 *
 * Every method returns concrete data or throws; callers (checkUnpatchedVulnerabilities) translate a
 * throw into an 'unknown' criterion so a failed lookup never reads as a clean release.
 */
class SecurityAdvisoryData {

    /** Severities that count toward the criterion (medium or higher). */
    static final List<String> BLOCKING_SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM']

    /** The advisories index (an alias pointing at the current advisories index). */
    static final String ADVISORIES_INDEX = 'advisories'

    /**
     * Max CVE ids per advisories terms lookup. OpenSearch's default max_terms_count is 65536; a
     * conservative batch keeps payloads small and mirrors the security_advisories agent.
     */
    private static final int ADVISORIES_BATCH_SIZE = 1000

    /** Page size for scan/advisory searches, matching the agent's default query size. */
    private static final int QUERY_SIZE = 1000

    def script
    SecurityAdvisoriesQuery advisoriesQuery

    SecurityAdvisoryData(String advisoriesUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, def script) {
        this.script = script
        this.advisoriesQuery = new SecurityAdvisoriesQuery(advisoriesUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    /**
     * Maps a release version to the canonical branch tag the scans are keyed on (project.tag).
     * Scans store release branches as origin/{major}.{minor}; the exact three-part tag (3.8.0) is
     * only created at release time, so it is never used here.
     *
     *   - already origin/-prefixed -> returned as-is
     *   - main / latest            -> origin/main
     *   - 3.8.0 (three-part)       -> origin/3.8
     *   - 3.8   (two-part)         -> origin/3.8
     *   - anything else            -> returned as-is
     */
    static String resolveVersionTag(String version) {
        if (!version) {
            return version
        }
        if (version.startsWith('origin/')) {
            return version
        }
        if (version.toLowerCase() in ['main', 'latest']) {
            return 'origin/main'
        }
        def parts = version.tokenize('.')
        if (parts.size() >= 2 && parts[0].isInteger() && parts[1].isInteger()) {
            return "origin/${parts[0]}.${parts[1]}"
        }
        return version
    }

    /**
     * Resolves the most recently created scans index. Scan indices are named scans-NNNNNN, so a
     * cluster-wide search for docs that have timestamp.scan, sorted by _index descending, returns
     * the highest-numbered (newest) index first.
     *
     * @return the concrete scans index name (e.g. scans-000164)
     * @throws RuntimeException if no scans index can be resolved
     */
    String getLatestScansIndex() {
        def query = shellEscape([
            size   : 1,
            query  : [exists: [field: 'timestamp.scan']],
            sort   : [['_index': [order: 'desc']]],
            _source: false
        ])
        def response = advisoriesQuery.searchAllIndices(query)
        def hits = response?.hits?.hits
        if (!hits) {
            throw new RuntimeException('Could not resolve latest scans index: no scan documents found.')
        }
        return hits[0]._index
    }

    /**
     * Returns the open (non-excluded) CVE ids scanned for the branch tag, keyed by project name so a
     * blocking CVE is attributable to a component. Collapsing on project.name keeps each project's
     * latest scan. Projects with no open vulnerabilities are omitted.
     *
     * @param scansIndex the concrete scans index (from getLatestScansIndex)
     * @param branchTag  the resolved project.tag (from resolveVersionTag)
     * @return map of project name -> sorted list of its open CVE ids
     */
    Map<String, List<String>> getOpenVulnerabilitiesByProject(String scansIndex, String branchTag) {
        // project.name is carried through so results can be scoped to release components once that
        // filter lands upstream (security-advisories#132).
        def query = shellEscape([
            size    : QUERY_SIZE,
            sort    : [['timestamp.scan': [order: 'desc']]],
            collapse: [field: 'project.name'],
            _source : ['project.name', 'vulnerabilities.id', 'vulnerabilities.excluded'],
            query   : [bool: [filter: [[term: ['project.tag': branchTag]]]]]
        ])
        def response = advisoriesQuery.search(scansIndex, query)
        def hits = response?.hits?.hits ?: []
        Map<String, List<String>> vulnerabilitiesByProject = [:]
        hits.each { hit ->
            String projectName = hit._source?.project?.name
            if (!projectName) {
                return
            }
            Set<String> openCves = [] as Set
            (hit._source?.vulnerabilities ?: []).each { vuln ->
                if (!vuln.excluded && vuln.id) {
                    openCves.add(vuln.id)
                }
            }
            if (openCves) {
                vulnerabilitiesByProject[projectName] = openCves.toList().sort()
            }
        }
        return vulnerabilitiesByProject
    }

    /**
     * Of the given CVE ids, returns those the advisories index reports as medium/high/critical AND
     * published on or before the cutoff (i.e. publicly known long enough to breach the age window).
     *
     * Matches on the advisory aliases field rather than id: advisory re-keying can change id while
     * aliases retains every known identifier, so aliases is the resilient join key (same rationale
     * as the security_advisories agent). The lookup is batched to stay within OpenSearch term limits.
     *
     * @param cveIds    open CVE ids to check (the flattened values of getOpenVulnerabilitiesByProject)
     * @param cutoffIso publish-age cutoff as an ISO-8601 string; advisories with
     *                  timestamp.publish <= cutoff breach the window
     * @return the subset of cveIds that are aged and medium-or-higher, as a sorted list
     */
    List<String> getAgedMediumOrHigherAdvisories(List<String> cveIds, String cutoffIso) {
        if (!cveIds) {
            return []
        }
        Set<String> matched = [] as Set
        cveIds.collate(ADVISORIES_BATCH_SIZE).each { batch ->
            Set<String> batchSet = batch as Set
            def query = shellEscape([
                size   : batch.size(),
                _source: ['aliases'],
                query  : [bool: [filter: [
                    [terms: [aliases: batch]],
                    [range: ['timestamp.publish': [lte: cutoffIso]]],
                    [terms: [severity: BLOCKING_SEVERITIES]]
                ]]]
            ])
            def response = advisoriesQuery.search(ADVISORIES_INDEX, query)
            def hits = response?.hits?.hits ?: []
            hits.each { hit ->
                (hit._source?.aliases ?: []).each { alias ->
                    if (batchSet.contains(alias)) {
                        matched.add(alias)
                    }
                }
            }
        }
        return matched.toList().sort()
    }

    /**
     * Serializes a query map to JSON and escapes the double quotes so it survives being passed
     * inside the double-quoted curl -d "..." argument (same convention as ReleaseStateData).
     */
    private static String shellEscape(Map queryMap) {
        return JsonOutput.toJson(queryMap).replace('"', '\\"')
    }
}
