/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.Test
import jenkins.ReleaseCriterion
import jenkins.ReleaseDecision
import jenkins.ReleaseSchedule

/**
 * Validation and serialization tests for the typed release state documents.
 */
class TestReleaseStateDocuments {

    private static final String TS = '2026-08-01T17:00:00Z'

    // ---- ReleaseCriterion ----

    @Test
    void testCriterionToDocumentMapsSnakeCaseAndDiscriminator() {
        def doc = new ReleaseCriterion([
                version           : '3.8.0',
                criterionType     : 'entrance',
                criterionName     : 'documentation_PRs_up',
                status            : 'not_met',
                product           : 'opensearch',
                source            : 'chore_check',
                daysToRelease     : 9,
                blockingComponents: ['issue-1']
        ]).toDocument(TS)

        assert doc.doc_type == 'criterion'
        assert doc.criterion_type == 'entrance'
        assert doc.criterion_name == 'documentation_PRs_up'
        assert doc.days_to_release == 9
        assert doc.blocking_components == ['issue-1']
        assert doc.last_checked == TS
    }

    @Test
    void testCriterionDefaultsBlockingComponentsToEmptyList() {
        def doc = new ReleaseCriterion([
                version: '3.8.0', criterionType: 'entrance', criterionName: 'x', status: 'unknown'
        ]).toDocument(TS)
        assert doc.blocking_components == []
    }

    @Test(expected = IllegalArgumentException)
    void testCriterionRejectsMissingRequiredField() {
        // criterionName missing
        new ReleaseCriterion([version: '3.8.0', criterionType: 'entrance', status: 'met'])
    }

    @Test(expected = IllegalArgumentException)
    void testCriterionRejectsInvalidStatus() {
        new ReleaseCriterion([version: '3.8.0', criterionType: 'entrance', criterionName: 'x', status: 'maybe'])
    }

    @Test(expected = IllegalArgumentException)
    void testCriterionRejectsInvalidType() {
        new ReleaseCriterion([version: '3.8.0', criterionType: 'sideways', criterionName: 'x', status: 'met'])
    }

    @Test(expected = IllegalArgumentException)
    void testCriterionRejectsInvalidProduct() {
        new ReleaseCriterion([version: '3.8.0', criterionType: 'entrance', criterionName: 'x', status: 'met', product: 'logstash'])
    }

    // ---- ReleaseDecision ----

    @Test
    void testDecisionToDocumentMapsSnakeCaseAndDiscriminator() {
        def doc = new ReleaseDecision([
                version            : '3.8.0',
                decidedBy          : 'test-rm',
                decision           : 'go',
                oscarRecommendation: 'yellow',
                agreedWithOscar    : false
        ]).toDocument(TS)

        assert doc.doc_type == 'decision'
        assert doc.decided_by == 'test-rm'
        assert doc.oscar_recommendation == 'yellow'
        assert doc.agreed_with_oscar == false
        assert doc.decided_at == TS
    }

    @Test
    void testDecisionDefaultsCriteriaSnapshotToEmptyMap() {
        def doc = new ReleaseDecision([version: '3.8.0', decidedBy: 'rm', decision: 'hold']).toDocument(TS)
        assert doc.criteria_snapshot == [:]
    }

    @Test(expected = IllegalArgumentException)
    void testDecisionRejectsInvalidDecision() {
        new ReleaseDecision([version: '3.8.0', decidedBy: 'rm', decision: 'maybe'])
    }

    @Test(expected = IllegalArgumentException)
    void testDecisionRejectsInvalidRecommendation() {
        new ReleaseDecision([version: '3.8.0', decidedBy: 'rm', decision: 'go', oscarRecommendation: 'purple'])
    }

    @Test(expected = IllegalArgumentException)
    void testDecisionRejectsMissingDecidedBy() {
        new ReleaseDecision([version: '3.8.0', decision: 'go'])
    }

    // ---- ReleaseSchedule ----

    @Test
    void testScheduleToDocumentMapsSnakeCaseAndDefaultsStatus() {
        def doc = new ReleaseSchedule([
                version       : '3.8.0',
                rcDate        : '2026-08-01',
                releaseDate   : '2026-08-12',
                releaseManager: 'test-rm',
                registeredBy  : 'release-schedule-job #5'
        ]).toDocument(TS)

        assert doc.version == '3.8.0'
        assert doc.rc_date == '2026-08-01'
        assert doc.release_date == '2026-08-12'
        assert doc.release_manager == 'test-rm'
        assert doc.registered_by == 'release-schedule-job #5'
        assert doc.status == 'active'
        assert doc.registered_at == TS
    }

    @Test
    void testScheduleHonorsExplicitStatus() {
        def doc = new ReleaseSchedule([version: '3.8.0', status: 'released']).toDocument(TS)
        assert doc.status == 'released'
    }

    @Test(expected = IllegalArgumentException)
    void testScheduleRejectsInvalidStatus() {
        new ReleaseSchedule([version: '3.8.0', status: 'paused'])
    }

    @Test(expected = IllegalArgumentException)
    void testScheduleRejectsMissingVersion() {
        new ReleaseSchedule([rcDate: '2026-08-01'])
    }
}
