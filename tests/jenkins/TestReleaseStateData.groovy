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

    @Before
    void setUp() {
        indexedDocs = []
        responseCode = '201'
        script = new Expando()
        script.sh = { Map args ->
            String s = args.script
            if (s.contains('-XPOST')) {
                indexedDocs.add([index: extractIndex(s), doc: extractBody(s)])
            }
            return responseCode
        }
        releaseStateData = new ReleaseStateData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    private String extractIndex(String shScript) {
        def matcher = (shScript =~ /${metricsUrl}\/([^\/]+)\/_doc/)
        return matcher ? matcher[0][1] : null
    }

    private Map extractBody(String shScript) {
        def matcher = (shScript =~ /-d '(\{.*\})'/)
        return matcher ? new JsonSlurper().parseText(matcher[0][1]) : [:]
    }

    @Test
    void testRegisterScheduleRoutesToScheduleIndexAndStampsTimestamp() {
        releaseStateData.registerSchedule(new ReleaseSchedule([version: '3.8.0']))
        assert indexedDocs[0].index == ReleaseStateIndex.SCHEDULE_INDEX
        // registered_at is stamped by ReleaseStateData, not the caller
        assert indexedDocs[0].doc.registered_at ==~ /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z/
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
