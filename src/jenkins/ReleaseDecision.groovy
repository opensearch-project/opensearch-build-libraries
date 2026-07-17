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
 * A Go/No-Go decision record for the opensearch_release_state index.
 * Validates its enum-like fields on construction so a malformed decision never reaches the cluster.
 */
class ReleaseDecision {

    static final List<String> VALID_DECISIONS = ['go', 'no-go', 'hold']
    static final List<String> VALID_RECOMMENDATIONS = ['red', 'yellow', 'green']

    String version
    String decidedBy
    String decision
    String oscarRecommendation
    Boolean agreedWithOscar
    Map criteriaSnapshot
    String releaseIssue
    String notes

    ReleaseDecision(Map args) {
        this.version = required(args, 'version')
        this.decidedBy = required(args, 'decidedBy')
        this.decision = requireOneOf(args, 'decision', VALID_DECISIONS)
        this.oscarRecommendation = optionalOneOf(args, 'oscarRecommendation', VALID_RECOMMENDATIONS)
        this.agreedWithOscar = args.agreedWithOscar
        this.criteriaSnapshot = args.criteriaSnapshot ?: [:]
        this.releaseIssue = args.releaseIssue
        this.notes = args.notes
    }

    /**
     * @param timestamp ISO-8601 time the decision was recorded, injected by the writer.
     */
    Map toDocument(String timestamp) {
        return [
            doc_type            : 'decision',
            version             : version,
            decided_at          : timestamp,
            decided_by          : decidedBy,
            decision            : decision,
            oscar_recommendation: oscarRecommendation,
            agreed_with_oscar   : agreedWithOscar,
            criteria_snapshot   : criteriaSnapshot,
            release_issue       : releaseIssue,
            notes               : notes
        ]
    }

    private static String required(Map args, String key) {
        if (!args[key]) {
            throw new IllegalArgumentException("ReleaseDecision: '${key}' is required.")
        }
        return args[key]
    }

    private static String requireOneOf(Map args, String key, List<String> allowed) {
        String value = required(args, key)
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("ReleaseDecision: '${key}' must be one of ${allowed}, got '${value}'.")
        }
        return value
    }

    private static String optionalOneOf(Map args, String key, List<String> allowed) {
        String value = args[key]
        if (value != null && !allowed.contains(value)) {
            throw new IllegalArgumentException("ReleaseDecision: '${key}' must be one of ${allowed}, got '${value}'.")
        }
        return value
    }
}
