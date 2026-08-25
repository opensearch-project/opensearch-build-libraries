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
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import utils.OpenSearchMetricsQuery
import jenkins.ReleaseCriterionCatalog

/**
 * Indexes release state documents on the OpenSearch metrics cluster.
 *
 * Writes to two indices:
 *  - opensearch_release_schedule: one schedule doc per release version
 *  - opensearch_release_state: per-criterion state docs and Go/No-Go decision docs
 *
 * Documents are typed (ReleaseSchedule, ReleaseCriterion, ReleaseDecision) and validated on
 * construction. This class stamps the write timestamp and appends the document (a new document
 * is created on each write), preserving history so dashboards can chart change over time.
 */
class ReleaseStateData {

    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    def script
    OpenSearchMetricsQuery metricsQuery

    ReleaseStateData(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.script = script
        this.metricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    /**
     * Upserts the schedule doc for a version. The document id is derived from the version so that
     * re-registering a release (e.g. to recompute status as its dates approach) overwrites the same
     * doc rather than appending a duplicate; the schedule index holds exactly one row per version.
     */
    void registerSchedule(ReleaseSchedule schedule) {
        metricsQuery.indexDocument(ReleaseIndices.SCHEDULE, schedule.toDocument(nowIso()), scheduleDocumentId(schedule.version))
    }

    /**
     * Deterministic document id for a version's schedule doc: a UUID derived from the version, so the
     * same version always maps to the same id (idempotent upsert).
     */
    private static String scheduleDocumentId(String version) {
        return UUID.nameUUIDFromBytes("release-schedule-${version}".getBytes('UTF-8')).toString()
    }

    void indexCriterion(ReleaseCriterion criterion) {
        metricsQuery.indexDocument(ReleaseIndices.STATE, criterion.toDocument(nowIso()))
    }

    void indexDecision(ReleaseDecision decision) {
        metricsQuery.indexDocument(ReleaseIndices.STATE, decision.toDocument(nowIso()))
    }

    /**
     * Returns the currently active releases from the schedule index, one entry per version.
     *
     * Because the schedule index holds exactly one doc per version (upserted by version-derived id),
     * a simple status filter is sufficient; no per-version dedup is needed. Each returned map carries
     * the fields the state orchestrator needs to build criteria: version, release_date, release_issue.
     *
     * @return list of maps [version, releaseDate, releaseIssue]; empty when no releases are active.
     */
    List<Map> getActiveReleases() {
        def response = metricsQuery.fetchMetricsFromIndex(ReleaseIndices.SCHEDULE, activeReleasesQuery())
        def hits = response?.hits?.hits
        if (!hits) {
            return []
        }
        return hits.collect { hit ->
            def doc = hit._source
            [
                version     : doc.version,
                releaseDate : doc.release_date,
                releaseIssue: doc.release_issue
            ]
        }
    }

    private String activeReleasesQuery() {
        def queryMap = [
            size : 100,
            query: [
                bool: [
                    filter: [
                        [match_phrase: [status: 'active']]
                    ]
                ]
            ]
        ]
        return JsonOutput.toJson(queryMap).replace('"', '\\"')
    }

    /**
     * Returns the latest chore-verified status for each criterion of a version, keyed by criterion
     * name then by product. The state index appends a new doc every run, so a criterion accumulates
     * many docs over time; sorting by last_checked descending and keeping the first doc seen per
     * (criterion, product) keeps only the most recent status for each. Keying by product keeps a
     * per-product criterion (e.g. integration tests, indexed once per product) from overwriting the
     * other product's status. Only chore_check docs are read; manual (issue_table) criteria are
     * owned by the release manager and are never written back.
     *
     * @param version the release version to read statuses for
     * @return map of criterion name -> (product -> latest status)
     */
    Map<String, Map<String, String>> getLatestChoreStatuses(String version) {
        def response = metricsQuery.fetchMetricsFromIndex(ReleaseIndices.STATE, latestChoreStatusesQuery(version))
        def hits = response?.hits?.hits ?: []
        Map<String, Map<String, String>> statusByCriterion = [:]
        hits.each { hit ->
            def doc = hit._source
            if (doc?.criterion_name && doc?.product && doc?.status) {
                Map<String, String> byProduct = statusByCriterion.get(doc.criterion_name, [:])
                // Hits are sorted newest first, so the first status seen per product is the latest.
                if (!byProduct.containsKey(doc.product)) {
                    byProduct[doc.product] = doc.status
                }
            }
        }
        return statusByCriterion
    }

    private String latestChoreStatusesQuery(String version) {
        def queryMap = [
            size   : 100,
            sort   : [['last_checked': [order: 'desc']]],
            _source: ['criterion_name', 'product', 'status'],
            query  : [
                bool: [
                    filter: [
                        [term: [doc_type: 'criterion']],
                        [term: [version: version]],
                        [term: [source: 'chore_check']]
                    ]
                ]
            ]
        ]
        return JsonOutput.toJson(queryMap).replace('"', '\\"')
    }

    private static final Map<String, String> CIRCLE_STATUS = [
        green_circle : 'met',
        yellow_circle: 'in_progress',
        red_circle   : 'not_met'
    ]

    private static final Map<String, String> STATUS_CIRCLE = [
        met        : ':green_circle:',
        in_progress: ':yellow_circle:',
        not_met    : ':red_circle:'
    ]

    /**
     * The three criteria tables in the release issue and the product each applies to: the entrance
     * table covers both products, and each exit table covers one product. start/stop bound the table
     * in the body so rows are parsed and rewritten in isolation.
     */
    private static List<Map> criteriaTables() {
        return [
            [type: 'entrance', product: 'both', start: /(?im)^#+.*entrance criteria/, stop: 'exit'],
            [type: 'exit', product: 'opensearch', start: /(?im)^#+\s+opensearch\s+\S+\s+\[?exit criteria/, stop: 'dashboards'],
            [type: 'exit', product: 'opensearch-dashboards', start: /(?im)^#+.*dashboards\s+\S+\s+\[?exit criteria/, stop: null]
        ]
    }

    /**
     * Rewrites the status circle of each chore-verified criterion row to reflect its latest indexed
     * status. The body is walked once, tracking which table each line falls under (the entrance table
     * applies to both products, each exit table to its own), so a per-product criterion gets that
     * table's product's status and a 'both' criterion applies in every table it appears in. A row is
     * matched by the chore keyword in the Criteria cell and only its status cell is rewritten. Lines
     * outside the three known tables, unmatched rows, and statuses with no circle (e.g. unknown) are
     * left as-is, so an unchanged body round-trips and a transient failure never blanks a circle.
     *
     * @param issueBody the raw markdown body of the release issue
     * @param statusByCriterion criterion name -> (product -> status), from getLatestChoreStatuses
     * @return the issue body with chore circles updated
     */
    String applyChoreStatusCircles(String issueBody, Map<String, Map<String, String>> statusByCriterion) {
        def chores = ReleaseCriterionCatalog.values().findAll { it.source == ReleaseCriterionCatalog.SOURCE_CHORE }
        def tables = criteriaTables()
        String currentProduct = null
        return issueBody.split(/(?<=\n)/).collect { line ->
            def startedTable = tables.find { (line =~ it.start).find() }
            if (startedTable) {
                currentProduct = startedTable.product
                return line
            }
            if (!currentProduct || !line.contains('|')) {
                return line
            }
            String criteriaCell = line.split('\\|')[0].toLowerCase()
            def criterion = chores.find { criteriaCell.contains(it.keyword) }
            if (!criterion) {
                return line
            }
            String status = statusByCriterion[criterion.criterionName]?.get(currentProduct)
            String circle = status ? STATUS_CIRCLE[status] : null
            if (!circle) {
                return line
            }
            return line.replaceFirst(/^([^|]*\|[^|]*?):(green|yellow|red)_circle:/, '$1' + java.util.regex.Matcher.quoteReplacement(circle))
        }.join('')
    }

    /**
     * Reads the manual criteria (those no chore verifies) from the release issue's criteria tables.
     *
     * The issue holds three tables: an entrance table that applies to both products, and one exit
     * table per product. A criterion's product is therefore the table it sits in, not the row itself.
     * Manual rows are matched by a stable keyword from their prose (see ReleaseCriterionCatalog) and
     * their status circle maps to a criterion status via CIRCLE_STATUS (unrecognised -> unknown).
     *
     * @param issueBody the raw markdown body of the release issue
     * @return list of maps [name, type, product, status], one per manual row found
     */
    List<Map> parseManualCriteria(String issueBody) {
        return criteriaTables().collectMany { table ->
            def criteria = ReleaseCriterionCatalog.values().findAll { it.source == ReleaseCriterionCatalog.SOURCE_ISSUE_TABLE && it.criterionType == table.type }
            sectionBetween(issueBody, table.start, table.stop).readLines().collectMany { line ->
                if (!line.contains('|')) {
                    return []
                }
                // Use .collect { it.trim() } rather than the spread operator (*.trim()):
                // Jenkins CPS transformation does not support spread, and this runs in a CPS context.
                List<String> cells = line.split('\\|').collect { it.trim() }
                // A leading pipe (| a | b |) splits to an empty first cell; drop it so the Criteria
                // and Status columns are at the same indices whether or not the row is pipe-bounded.
                if (cells && cells[0].isEmpty()) {
                    cells = cells.drop(1)
                }
                // Match the keyword against the Criteria cell only so prose in the Description or
                // Comments columns can never produce a spurious criterion.
                String criteriaCell = (cells ? cells[0] : '').toLowerCase()
                String statusCell = cells.size() > 1 ? cells[1] : ''
                criteria.findAll { criteriaCell.contains(it.keyword) }.collect { criterion ->
                    [name: criterion.criterionName, type: table.type, product: table.product, status: statusFor(statusCell)]
                }
            }
        }
    }

    /**
     * Returns the slice of the body from the heading matching startPattern up to the next heading
     * whose text contains stopKeyword (or end of body when stopKeyword is null), so each criteria
     * table is parsed in isolation.
     */
    private static String sectionBetween(String body, String startPattern, String stopKeyword) {
        def start = (body =~ startPattern)
        if (!start.find()) {
            return ''
        }
        String rest = body.substring(start.end())
        if (stopKeyword) {
            def stop = (rest =~ /(?im)^#+.*${Pattern.quote(stopKeyword)}/)
            if (stop.find()) {
                return rest.substring(0, stop.start())
            }
        }
        return rest
    }

    private static String statusFor(String statusCell) {
        def match = CIRCLE_STATUS.find { circle, status -> statusCell.contains(circle) }
        return match ? match.value : 'unknown'
    }

    /**
     * Current UTC timestamp in ISO-8601 format (e.g. 2026-08-01T17:00:00Z), which OpenSearch
     * parses via the default strict_date_optional_time format.
     */
    private String nowIso() {
        def formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        formatter.setTimeZone(TimeZone.getTimeZone('UTC'))
        return formatter.format(new Date())
    }
}
