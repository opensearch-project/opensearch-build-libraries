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
 * A release schedule record for the opensearch_release_schedule index (one per version).
 * Validates its enum-like fields on construction so a malformed schedule never reaches the cluster.
 */
class ReleaseSchedule {

    static final List<String> VALID_STATUSES = ['active', 'released', 'cancelled']

    String version
    String rcDate
    String releaseDate
    String releaseIssue
    String releaseManager
    String status
    String registeredBy

    ReleaseSchedule(Map args) {
        this.version = required(args, 'version')
        this.status = args.status ?: 'active'
        if (!VALID_STATUSES.contains(this.status)) {
            throw new IllegalArgumentException("ReleaseSchedule: 'status' must be one of ${VALID_STATUSES}, got '${this.status}'.")
        }
        this.rcDate = args.rcDate
        this.releaseDate = args.releaseDate
        this.releaseIssue = args.releaseIssue
        this.releaseManager = args.releaseManager
        this.registeredBy = args.registeredBy
    }

    /**
     * @param timestamp ISO-8601 time the schedule was registered, injected by the writer.
     */
    Map toDocument(String timestamp) {
        return [
            version        : version,
            rc_date        : rcDate,
            release_date   : releaseDate,
            release_issue  : releaseIssue,
            release_manager: releaseManager,
            status         : status,
            registered_at  : timestamp,
            registered_by  : registeredBy
        ]
    }

    private static String required(Map args, String key) {
        if (!args[key]) {
            throw new IllegalArgumentException("ReleaseSchedule: '${key}' is required.")
        }
        return args[key]
    }
}
