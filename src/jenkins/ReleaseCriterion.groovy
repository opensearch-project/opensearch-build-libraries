/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

/**
 * A single per-criterion release readiness record for the opensearch_release_state index.
 * Validates its enum-like fields on construction so a malformed criterion never reaches the cluster.
 */
class ReleaseCriterion {

    static final List<String> VALID_TYPES = ['entrance', 'exit']
    static final List<String> VALID_STATUSES = ['met', 'not_met', 'in_progress', 'unknown', 'not_applicable']
    static final List<String> VALID_PRODUCTS = ['opensearch', 'opensearch-dashboards', 'both']
    static final List<String> VALID_SOURCES = ['issue_table', 'chore_check', 'manual']

    String version
    String releaseDate
    Integer daysToRelease
    String product
    String criterionType
    String criterionName
    String status
    String details
    List<String> blockingComponents
    String source
    String releaseIssue
    String checkedBy

    ReleaseCriterion(Map args) {
        this.version = required(args, 'version')
        this.criterionType = requireOneOf(args, 'criterionType', VALID_TYPES)
        this.criterionName = required(args, 'criterionName')
        this.status = requireOneOf(args, 'status', VALID_STATUSES)
        this.product = optionalOneOf(args, 'product', VALID_PRODUCTS)
        this.source = optionalOneOf(args, 'source', VALID_SOURCES)
        this.releaseDate = args.releaseDate
        this.daysToRelease = args.daysToRelease
        this.details = args.details
        this.blockingComponents = args.blockingComponents ?: []
        this.releaseIssue = args.releaseIssue
        this.checkedBy = args.checkedBy
    }

    /**
     * @param timestamp ISO-8601 time the criterion was checked, injected by the writer.
     */
    Map toDocument(String timestamp) {
        return [
            doc_type           : 'criterion',
            version            : version,
            release_date       : releaseDate,
            days_to_release    : daysToRelease,
            product            : product,
            criterion_type     : criterionType,
            criterion_name     : criterionName,
            status             : status,
            details            : details,
            blocking_components: blockingComponents,
            source             : source,
            release_issue      : releaseIssue,
            checked_by         : checkedBy,
            last_checked       : timestamp
        ]
    }

    private static String required(Map args, String key) {
        if (!args[key]) {
            throw new IllegalArgumentException("ReleaseCriterion: '${key}' is required.")
        }
        return args[key]
    }

    private static String requireOneOf(Map args, String key, List<String> allowed) {
        String value = required(args, key)
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("ReleaseCriterion: '${key}' must be one of ${allowed}, got '${value}'.")
        }
        return value
    }

    private static String optionalOneOf(Map args, String key, List<String> allowed) {
        String value = args[key]
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException("ReleaseCriterion: '${key}' must be one of ${allowed}, got '${value}'.")
        }
        return value
    }
}
