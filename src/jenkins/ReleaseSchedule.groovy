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
 * A release schedule record for the opensearch_release_schedule index (one per version).
 * Validates its enum-like fields on construction so a malformed schedule never reaches the cluster.
 */
class ReleaseSchedule {

    static final List<String> VALID_STATUSES = ['inactive', 'active', 'released', 'cancelled']

    String version
    String rcDate
    String releaseDate
    String releaseIssue
    String releaseManager
    String status
    String registeredBy

    ReleaseSchedule(Map args) {
        String context = this.class.simpleName
        this.version = ArgumentValidator.required(args, 'version', context)
        // status defaults to 'inactive' when absent, then must be one of the allowed values.
        // A release stays 'inactive' until ~1 month before its RC date, when it is flipped to 'active'.
        this.status = ArgumentValidator.requireOneOf([status: args.status ?: 'inactive'], 'status', VALID_STATUSES, context)
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
}
