/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import utils.OpenSearchMetricsQuery

/**
 * Creates and manages the release state indices on the OpenSearch metrics cluster.
 *
 * Owns the mapping definitions for:
 *  - opensearch_release_schedule: one doc per release version (dates, RM, status)
 *  - opensearch_release_state: per-criterion readiness state + Go/No-Go decision docs
 *
 * All cluster I/O is delegated to OpenSearchMetricsQuery. Index creation is idempotent:
 * an existing index is left untouched. Mapping changes to an already-created index are
 * not reconciled here and require a separate migration.
 */
class ReleaseStateIndex {

    public static final String SCHEDULE_INDEX = ReleaseIndices.SCHEDULE
    public static final String STATE_INDEX = ReleaseIndices.STATE

    def script
    OpenSearchMetricsQuery metricsQuery

    ReleaseStateIndex(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, def script) {
        this.script = script
        // createIndex/indexExists take the target index per call, so no default indexName is bound here.
        this.metricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    void createIndicesIfNotExist() {
        createIndexIfNotExist(SCHEDULE_INDEX, scheduleMapping())
        createIndexIfNotExist(STATE_INDEX, stateMapping())
    }

    void createIndexIfNotExist(String indexName, Map mapping) {
        if (metricsQuery.indexExists(indexName)) {
            this.script.echo("Index ${indexName} already exists. Skipping creation.")
            return
        }
        this.script.echo("Creating index ${indexName}...")
        metricsQuery.createIndex(indexName, mapping)
        this.script.echo("Index ${indexName} created successfully.")
    }

    static Map scheduleMapping() {
        return [
            mappings: [
                properties: [
                    version        : [type: 'keyword'],
                    rc_date        : [type: 'date', format: 'yyyy-MM-dd'],
                    release_date   : [type: 'date', format: 'yyyy-MM-dd'],
                    release_issue  : [type: 'keyword'],
                    release_manager: [type: 'keyword'],
                    status         : [type: 'keyword'],
                    registered_at  : [type: 'date'],
                    registered_by  : [type: 'keyword']
                ]
            ]
        ]
    }

    static Map stateMapping() {
        return [
            mappings: [
                properties: [
                    doc_type            : [type: 'keyword'],
                    version             : [type: 'keyword'],
                    release_date        : [type: 'date', format: 'yyyy-MM-dd'],
                    days_to_release     : [type: 'integer'],
                    product             : [type: 'keyword'],
                    criterion_type      : [type: 'keyword'],
                    criterion_name      : [type: 'keyword'],
                    status              : [type: 'keyword'],
                    details             : [type: 'text', fields: [keyword: [type: 'keyword', ignore_above: 1024]]],
                    blocking_components : [type: 'keyword'],
                    source              : [type: 'keyword'],
                    release_issue       : [type: 'keyword'],
                    checked_by          : [type: 'keyword'],
                    last_checked        : [type: 'date'],
                    decided_at          : [type: 'date'],
                    decided_by          : [type: 'keyword'],
                    decision            : [type: 'keyword'],
                    oscar_recommendation: [type: 'keyword'],
                    agreed_with_oscar   : [type: 'boolean'],
                    criteria_snapshot   : [type: 'object', enabled: false],
                    notes               : [type: 'text', fields: [keyword: [type: 'keyword', ignore_above: 1024]]]
                ]
            ]
        ]
    }
}
