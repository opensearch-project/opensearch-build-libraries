/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.Before
import org.junit.Test
import groovy.json.JsonSlurper
import jenkins.ReleaseStateData
import jenkins.ReleaseStateIndex
import jenkins.ReleaseCriterion
import jenkins.ReleaseDecision
import jenkins.ReleaseSchedule

class TestReleaseStateData {
    private final String metricsUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'

    private ReleaseStateData releaseStateData
    private def script
    // Captures each POSTed doc as [index: <targetIndex>, doc: <parsed body>]
    private List<Map> indexedDocs
    private String responseCode

    // Holds the body written by the most recent writeFile call, to pair with the following POST.
    private String pendingBody

    @Before
    void setUp() {
        indexedDocs = []
        pendingBody = null
        responseCode = '201'
        script = new Expando()
        // The body is written to a temp file first, then the POST curl references it.
        script.writeFile = { Map args -> pendingBody = args.text }
        script.sh = { Map args ->
            String s = args.script
            // State/decision docs are appended via POST /_doc; schedule docs are upserted via
            // PUT /_doc/<id>. Capture both, recording the id when the write targets a specific doc.
            if (s.contains('-XPOST') || s.contains('-XPUT')) {
                indexedDocs.add([index: extractIndex(s), id: extractId(s), doc: new JsonSlurper().parseText(pendingBody)])
            }
            return responseCode
        }
        releaseStateData = new ReleaseStateData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    private String extractIndex(String shScript) {
        def matcher = (shScript =~ /${metricsUrl}\/([^\/]+)\/_doc/)
        return matcher ? matcher[0][1] : null
    }

    // Returns the explicit document id when the write is PUT /_doc/<id>, or null for POST /_doc.
    private String extractId(String shScript) {
        def matcher = (shScript =~ /${metricsUrl}\/[^\/]+\/_doc\/([^"]+)/)
        return matcher ? matcher[0][1] : null
    }

    @Test
    void testRegisterScheduleRoutesToScheduleIndexAndStampsTimestamp() {
        releaseStateData.registerSchedule(new ReleaseSchedule([version: '3.8.0']))
        assert indexedDocs[0].index == ReleaseStateIndex.SCHEDULE_INDEX
        // registered_at is stamped by ReleaseStateData, not the caller
        assert indexedDocs[0].doc.registered_at ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z/
    }

    @Test
    void testRegisterScheduleUpsertsWithVersionDerivedId() {
        // The schedule doc is upserted at a deterministic, version-derived id so re-registering a
        // version overwrites the same doc instead of appending a duplicate. The expected value is a
        // fixed literal (independently computed as the UUID of "release-schedule-3.8.0"), not the
        // production derivation recomputed, so a change to the id scheme would fail this test.
        releaseStateData.registerSchedule(new ReleaseSchedule([version: '3.8.0']))
        assert indexedDocs[0].id == '53bfcef1-e2e9-315f-9d4a-156103378726'
        // A well-formed UUID that round-trips (no URL-unsafe characters).
        assert UUID.fromString(indexedDocs[0].id).toString() == indexedDocs[0].id
    }

    @Test
    void testRegisterScheduleSameVersionReusesIdDifferentVersionDiffers() {
        releaseStateData.registerSchedule(new ReleaseSchedule([version: '3.8.0']))
        releaseStateData.registerSchedule(new ReleaseSchedule([version: '3.8.0']))
        releaseStateData.registerSchedule(new ReleaseSchedule([version: '3.9.0']))
        // Same version -> same id (idempotent upsert), different version -> different id.
        assert indexedDocs[0].id == indexedDocs[1].id
        assert indexedDocs[0].id != indexedDocs[2].id
    }

    @Test
    void testIndexCriterionRoutesToStateIndexAndStampsTimestamp() {
        releaseStateData.indexCriterion(new ReleaseCriterion([
                version      : '3.8.0',
                criterionType: 'entrance',
                criterionName: 'documentation_PRs_up',
                status       : 'not_met'
        ]))
        assert indexedDocs[0].index == ReleaseStateIndex.STATE_INDEX
        assert indexedDocs[0].doc.doc_type == 'criterion'
        assert indexedDocs[0].doc.last_checked ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z/
        // Criterion docs are appended (server-assigned id) to preserve history, not upserted.
        assert indexedDocs[0].id == null
    }

    @Test
    void testIndexDecisionRoutesToStateIndexAndStampsTimestamp() {
        releaseStateData.indexDecision(new ReleaseDecision([
                version  : '3.8.0',
                decidedBy: 'test-rm',
                decision : 'go'
        ]))
        assert indexedDocs[0].index == ReleaseStateIndex.STATE_INDEX
        assert indexedDocs[0].doc.doc_type == 'decision'
        assert indexedDocs[0].doc.decided_at ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z/
    }

    @Test
    void testIndexCriterionThrowsWhenClusterReturnsError() {
        responseCode = '404'
        try {
            releaseStateData.indexCriterion(new ReleaseCriterion([
                    version: '3.8.0', criterionType: 'entrance', criterionName: 'x', status: 'unknown'
            ]))
            assert false : 'Expected RuntimeException when cluster returns a non-2xx status'
        } catch (RuntimeException e) {
            assert e.message.contains('Failed to index document')
        }
    }
}
