/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import utils.ArgumentValidator

/**
 * A release schedule record for the opensearch_release_schedule index (one per version).
 * Validates its enum-like fields on construction so a malformed schedule never reaches the cluster.
 */
class ReleaseSchedule {

    static final List<String> VALID_STATUSES = ['inactive', 'active', 'released', 'cancelled']
    // A release becomes 'active' once it is within this many days of its RC date.
    static final long ACTIVATION_WINDOW_DAYS = 30

    String version
    String rcDate
    String releaseDate
    String releaseIssue
    List<String> releaseManager
    String status
    String registeredBy

    ReleaseSchedule(Map args) {
        String context = this.class.simpleName
        this.version = ArgumentValidator.required(args, 'version', context)
        // An explicit status is honored (manual override, e.g. cancelled/forced active).
        // Otherwise the status is derived from the dates: 'inactive' until the activation window,
        // 'active' within ACTIVATION_WINDOW_DAYS of the RC date, and 'released' once the release
        // date has passed. Re-running registration recomputes this as the dates approach.
        String resolvedStatus = args.status ?: deriveStatus(args.rcDate, args.releaseDate)
        this.status = ArgumentValidator.requireOneOf([status: resolvedStatus], 'status', VALID_STATUSES, context)
        this.rcDate = args.rcDate
        this.releaseDate = args.releaseDate
        this.releaseIssue = args.releaseIssue
        // Accept a list (from the schedule parser) or a single handle (manual callers); normalize to a list.
        this.releaseManager = normalizeManagers(args.releaseManager)
        this.registeredBy = args.registeredBy
    }

    private static List<String> normalizeManagers(managers) {
        if (managers == null) {
            return []
        }
        return (managers instanceof List) ? managers : [managers]
    }

    /**
     * Derives the schedule status from the RC and release dates relative to today:
     *  - 'released' once the release date has passed
     *  - 'active' when today is within ACTIVATION_WINDOW_DAYS of the RC date (up to the release date)
     *  - 'inactive' when the RC date is further out, or the RC date is absent/unparseable (safe default)
     *
     * @param rcDate RC date in yyyy-MM-dd, or null
     * @param releaseDate release date in yyyy-MM-dd, or null
     * @param today reference date; defaults to LocalDate.now(). Overridable for testing.
     */
    static String deriveStatus(String rcDate, String releaseDate, LocalDate today = LocalDate.now()) {
        LocalDate release = parseOrNull(releaseDate)
        if (release != null && today.isAfter(release)) {
            return 'released'
        }
        LocalDate rc = parseOrNull(rcDate)
        if (rc == null) {
            return 'inactive'
        }
        long daysUntilRc = ChronoUnit.DAYS.between(today, rc)
        return (daysUntilRc <= ACTIVATION_WINDOW_DAYS) ? 'active' : 'inactive'
    }

    private static LocalDate parseOrNull(String date) {
        if (!date) {
            return null
        }
        try {
            return LocalDate.parse(date)
        } catch (Exception ignored) {
            return null
        }
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
