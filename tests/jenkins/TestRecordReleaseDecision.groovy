/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import groovy.json.JsonSlurperClassic
import org.junit.Before
import org.junit.Test

class TestRecordReleaseDecision extends BuildPipelineTest {

    def script
    private String scheduleHits
    private String criteriaHits
    private String indexHttpCode
    private Map<String, String> filesWritten
    private List<String> ghCommands

    @Override
    @Before
    void setUp() {
        super.setUp()
        filesWritten = [:]
        ghCommands = []
        indexHttpCode = '201'
        scheduleHits = """{"hits":{"hits":[{"_source":{"version":"3.8.0","release_date":"2026-08-12",
            "release_issue":"https://github.com/opensearch-project/opensearch-build/issues/6278"}}]}}"""
        criteriaHits = """{"hits":{"hits":[
            {"_source":{"criterion_name":"release_notes_ready","product":"both","status":"met","blocking_components":[]}},
            {"_source":{"criterion_name":"security_reviews_complete","product":"both","status":"not_met","blocking_components":["security"]}}
        ]}}"""

        binding.setVariable('METRICS_HOST_ACCOUNT', 'METRICS_HOST_ACCOUNT')
        binding.setVariable('GITHUB_USER', 'GITHUB_USER')
        binding.setVariable('GITHUB_TOKEN', 'GITHUB_TOKEN')
        binding.setVariable('env', [
            'METRICS_HOST_URL'     : 'metrics.url',
            'AWS_ACCESS_KEY_ID'    : 'abc',
            'AWS_SECRET_ACCESS_KEY': 'xyz',
            'AWS_SESSION_TOKEN'    : 'token'
        ])
        helper.registerAllowedMethod('withSecrets', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('writeFile', [Map], { Map args -> filesWritten[args.file] = args.text })
        helper.registerAllowedMethod('error', [String], { String message -> throw new Exception(message) })
        // Reads are routed by index; gh calls are recorded; anything else is the indexing POST or its
        // temp-file cleanup.
        helper.registerAllowedMethod('sh', [Map], { Map args ->
            String s = args.script
            if (s.contains('-XGET')) {
                return s.contains('opensearch_release_schedule') ? scheduleHits : criteriaHits
            }
            if (s.contains('gh issue')) {
                ghCommands.add(s)
                return ''
            }
            return s.contains('rm -f') ? '0' : indexHttpCode
        })
        script = loadScript('vars/recordReleaseDecision.groovy')
    }

    /** The decision document, parsed from the temp file the cluster write posts. */
    private Map indexedDecision() {
        def bodies = filesWritten.findAll { file, text -> file.startsWith('os-metrics-request') }
        assert bodies.size() == 1
        return new JsonSlurperClassic().parseText(bodies.values().first())
    }

    /** The comment body posted to the release issue, or null when none was posted. */
    private String postedComment() {
        return filesWritten['release-issue-comment.md']
    }

    @Test
    void testIndexesTheDecisionWithItsCriteriaSnapshot() {
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', oscarRecommendation: 'green')

        Map doc = indexedDecision()
        assert doc.doc_type == 'decision'
        assert doc.version == '3.8.0'
        assert doc.decision == 'go'
        assert doc.decided_by == 'U123'
        assert doc.oscar_recommendation == 'green'
        // The snapshot records what the decision was taken against, manual criteria included.
        assert doc.criteria_snapshot.keySet() == ['release_notes_ready', 'security_reviews_complete'] as Set
        assert doc.criteria_snapshot.security_reviews_complete.both.status == 'not_met'
    }

    @Test
    void testCommentsOnTheReleaseIssueResolvedFromTheSchedule() {
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', oscarRecommendation: 'green')

        assert ghCommands.any { it.contains('gh issue comment 6278') }
        assert postedComment().contains('Release decision: Go')
        assert postedComment().contains('U123')
    }

    @Test
    void testCommentListsEveryCriterionStatusAtDecisionTime() {
        // A reader of the issue should not have to query the index to see what was outstanding.
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', oscarRecommendation: 'red')

        String comment = postedComment()
        assert comment.contains('### Criteria at the time of this decision')
        assert comment.contains('| Criterion | Product | Status | Blocking |')
        assert comment.contains('| `release_notes_ready` | both | :green_circle: met | - |')
        assert comment.contains('| `security_reviews_complete` | both | :red_circle: not_met | security |')
    }

    @Test
    void testCommentListsAPerProductCriterionOncePerProduct() {
        // A release can be blocked on one product only, so collapsing the rows would hide that.
        criteriaHits = '''{"hits":{"hits":[
            {"_source":{"criterion_name":"all_integration_tests_passing","product":"opensearch","status":"met","blocking_components":[]}},
            {"_source":{"criterion_name":"all_integration_tests_passing","product":"opensearch-dashboards","status":"not_met","blocking_components":["alertingDashboards"]}}
        ]}}'''

        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123')

        String comment = postedComment()
        assert comment.contains('| `all_integration_tests_passing` | opensearch | :green_circle: met | - |')
        assert comment.contains('| `all_integration_tests_passing` | opensearch-dashboards | :red_circle: not_met | alertingDashboards |')
    }

    @Test
    void testCommentSaysSoWhenNoCriteriaWereIndexed() {
        criteriaHits = '{"hits":{"hits":[]}}'

        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123')

        assert postedComment().contains('No criteria state was indexed')
    }

    @Test
    void testAnUnrecognizedStatusIsShownRatherThanDropped() {
        criteriaHits = '''{"hits":{"hits":[
            {"_source":{"criterion_name":"release_notes_ready","product":"both","status":"unknown","blocking_components":[]}}
        ]}}'''

        script.call(version: '3.8.0', decision: 'hold', decidedBy: 'U123')

        assert postedComment().contains('| `release_notes_ready` | both | :white_circle: unknown | - |')
    }

    @Test
    void testDisplayNameNamesThePersonAndKeepsTheAuthenticatedId() {
        // The id is the part that is proven, so it stays even once there is a readable name.
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', decidedByDisplayName: 'Foo Bar')

        Map doc = indexedDecision()
        assert doc.decided_by == 'U123'
        assert doc.decided_by_display_name == 'Foo Bar'
        assert postedComment().contains('**Decided by:** Foo Bar (Slack U123)')
    }

    @Test
    void testFallsBackToTheIdWhenSlackResolvedNoName() {
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', decidedByDisplayName: '  ')

        assert postedComment().contains('**Decided by:** Slack U123')
    }

    @Test
    void testAgreesWithOscarWhenAGoFollowsGreen() {
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', oscarRecommendation: 'green')
        assert indexedDecision().agreed_with_oscar == true
    }

    @Test
    void testDisagreesWhenAGoOverridesANonGreenVerdict() {
        // Shipping over a red verdict is legitimate and the record has to show it happened.
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', oscarRecommendation: 'red')

        assert indexedDecision().agreed_with_oscar == false
        assert postedComment().contains('decision differs from the recommendation')
    }

    @Test
    void testNoGoOnARedVerdictAgrees() {
        script.call(version: '3.8.0', decision: 'no-go', decidedBy: 'U123', oscarRecommendation: 'red')
        assert indexedDecision().agreed_with_oscar == true
    }

    @Test
    void testAgreementIsUnknownWithoutARecommendation() {
        // No recommendation is not the same as overriding one.
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123')

        Map doc = indexedDecision()
        assert doc.agreed_with_oscar == null
        assert doc.oscar_recommendation == null
        assert !postedComment().contains('OSCAR recommendation')
    }

    @Test
    void testNotesAreRecordedAndCommented() {
        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123', oscarRecommendation: 'red',
            notes: 'Blocking CVE waived by the security team')

        assert indexedDecision().notes == 'Blocking CVE waived by the security team'
        assert postedComment().contains('Blocking CVE waived by the security team')
    }

    @Test
    void testAnExplicitReleaseIssueSkipsTheScheduleLookup() {
        script.call(version: '3.8.0', decision: 'hold', decidedBy: 'U123',
            releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/1')

        assert ghCommands.any { it.contains('gh issue comment 1 ') }
    }

    @Test
    void testDecisionIsStillIndexedWhenNoReleaseIssueIsKnown() {
        // The index is the durable record; a missing issue costs visibility, not the decision.
        scheduleHits = '{"hits":{"hits":[]}}'

        script.call(version: '3.8.0', decision: 'go', decidedBy: 'U123')

        assert indexedDecision().version == '3.8.0'
        assert ghCommands.isEmpty()
        assert postedComment() == null
    }

    @Test
    void testRequiredArgumentsAreValidated() {
        for (args in [[decision: 'go', decidedBy: 'U123'], [version: '3.8.0', decidedBy: 'U123'],
                      [version: '3.8.0', decision: 'go']]) {
            def thrown = null
            try {
                script.call(args)
            } catch (Exception e) {
                thrown = e
            }
            assert thrown?.message?.contains('required')
        }
        assert filesWritten.isEmpty()
    }

    @Test
    void testAnInvalidDecisionIsRejectedBeforeAnythingIsWritten() {
        def thrown = null
        try {
            script.call(version: '3.8.0', decision: 'maybe', decidedBy: 'U123')
        } catch (Exception e) {
            thrown = e
        }
        assert thrown != null
        assert ghCommands.isEmpty()
    }
}
