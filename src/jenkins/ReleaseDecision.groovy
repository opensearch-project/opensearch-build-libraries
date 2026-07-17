/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import utils.ArgumentValidator

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
        String context = this.class.simpleName
        this.version = ArgumentValidator.required(args, 'version', context)
        this.decidedBy = ArgumentValidator.required(args, 'decidedBy', context)
        this.decision = ArgumentValidator.requireOneOf(args, 'decision', VALID_DECISIONS, context)
        this.oscarRecommendation = ArgumentValidator.optionalOneOf(args, 'oscarRecommendation', VALID_RECOMMENDATIONS, context)
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
}
