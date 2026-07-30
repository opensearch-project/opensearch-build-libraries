/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import java.text.SimpleDateFormat
import utils.OpenSearchMetricsQuery

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
     * Current UTC timestamp in ISO-8601 format (e.g. 2026-08-01T17:00:00Z), which OpenSearch
     * parses via the default strict_date_optional_time format.
     */
    private String nowIso() {
        def formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        formatter.setTimeZone(TimeZone.getTimeZone('UTC'))
        return formatter.format(new Date())
    }
}
