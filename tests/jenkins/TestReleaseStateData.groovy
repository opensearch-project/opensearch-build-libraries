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
    // Search response returned to -XGET calls, and the index the search targeted.
    private String searchResponse
    private String searchedIndex
    private String searchBody

    // Holds the body written by the most recent writeFile call, to pair with the following POST.
    private String pendingBody

    @Before
    void setUp() {
        indexedDocs = []
        pendingBody = null
        responseCode = '201'
        searchResponse = '{"hits":{"hits":[]}}'
        searchedIndex = null
        script = new Expando()
        script.println = { msg -> }
        // The body is written to a temp file first, then the POST curl references it.
        script.writeFile = { Map args -> pendingBody = args.text }
        script.sh = { Map args ->
            String s = args.script
            // State/decision docs are appended via POST /_doc; schedule docs are upserted via
            // PUT /_doc/<id>. Capture both, recording the id when the write targets a specific doc.
            if (s.contains('-XPOST') || s.contains('-XPUT')) {
                indexedDocs.add([index: extractIndex(s), id: extractId(s), doc: new JsonSlurper().parseText(pendingBody)])
                return responseCode
            }
            if (s.contains('-XGET')) {
                searchedIndex = extractSearchIndex(s)
                searchBody = s
                return searchResponse
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

    private String extractSearchIndex(String shScript) {
        def matcher = (shScript =~ /${metricsUrl}\/([^\/]+)\/_search/)
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

    @Test
    void testGetActiveReleasesQueriesScheduleIndexAndMapsFields() {
        searchResponse = '''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "version": "3.8.0",
                      "release_date": "2026-08-15",
                      "release_issue": "https://github.com/opensearch-project/opensearch-build/issues/5152",
                      "status": "active"
                    }
                  },
                  {
                    "_source": {
                      "version": "2.19.7",
                      "release_date": "2026-09-01",
                      "release_issue": "https://github.com/opensearch-project/opensearch-build/issues/5200",
                      "status": "active"
                    }
                  }
                ]
              }
            }
        '''
        def active = releaseStateData.getActiveReleases()
        // Reads the schedule index and filters on active status.
        assert searchedIndex == ReleaseStateIndex.SCHEDULE_INDEX
        assert active.size() == 2
        assert active[0] == [version: '3.8.0', releaseDate: '2026-08-15', releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/5152']
        assert active[1].version == '2.19.7'
    }

    @Test
    void testGetActiveReleasesReturnsEmptyWhenNoActiveReleases() {
        searchResponse = '{"hits":{"hits":[]}}'
        assert releaseStateData.getActiveReleases() == []
    }

    @Test
    void testGetLatestChoreStatusesKeysStatusByCriterionAndProduct() {
        searchResponse = '''
            {
              "hits": {
                "hits": [
                  {"_source": {"criterion_name": "release_owners_assigned", "product": "both", "status": "met"}},
                  {"_source": {"criterion_name": "all_integration_tests_passing", "product": "opensearch", "status": "not_met"}},
                  {"_source": {"criterion_name": "all_integration_tests_passing", "product": "opensearch-dashboards", "status": "met"}}
                ]
              }
            }
        '''
        def statuses = releaseStateData.getLatestChoreStatuses('3.8.0')
        // A per-product criterion keeps both products' statuses instead of one overwriting the other.
        assert statuses == [
            release_owners_assigned      : [both: 'met'],
            all_integration_tests_passing: ['opensearch': 'not_met', 'opensearch-dashboards': 'met']
        ]
        assert searchedIndex == ReleaseStateIndex.STATE_INDEX
        assert searchBody.contains('criterion_name')
        assert searchBody.contains('last_checked')
        assert searchBody.contains('chore_check')
        assert searchBody.contains('3.8.0')
    }

    @Test
    void testGetLatestChoreStatusesKeepsNewestPerCriterionProduct() {
        // Hits are sorted newest first; the first status seen for a (criterion, product) wins.
        searchResponse = '''
            {
              "hits": {
                "hits": [
                  {"_source": {"criterion_name": "release_owners_assigned", "product": "both", "status": "met"}},
                  {"_source": {"criterion_name": "release_owners_assigned", "product": "both", "status": "not_met"}}
                ]
              }
            }
        '''
        assert releaseStateData.getLatestChoreStatuses('3.8.0') == [release_owners_assigned: [both: 'met']]
    }

    @Test
    void testGetLatestChoreStatusesReturnsEmptyWhenNoDocs() {
        searchResponse = '{"hits":{"hits":[]}}'
        assert releaseStateData.getLatestChoreStatuses('3.8.0') == [:]
    }

    @Test
    void testGetLatestChoreStatusesSkipsHitsMissingNameProductOrStatus() {
        // A hit missing criterion_name, product, or status is skipped; only the well-formed one is kept.
        searchResponse = '''
            {
              "hits": {
                "hits": [
                  {"_source": {"product": "both", "status": "met"}},
                  {"_source": {"criterion_name": "release_notes_ready", "status": "met"}},
                  {"_source": {"criterion_name": "code_coverage_not_decreased", "product": "both"}},
                  {"_source": {"criterion_name": "release_owners_assigned", "product": "both", "status": "not_met"}}
                ]
              }
            }
        '''
        assert releaseStateData.getLatestChoreStatuses('3.8.0') == [release_owners_assigned: [both: 'not_met']]
    }

    // The three criteria tables with a chore row in each, for the circle write-back.
    private static final String CIRCLE_BODY = '''### [Entrance Criteria](https://example.com)
Criteria | Status | Description  | Comments
-- | -- | -- | --
Each component release issue has an assigned owner | :yellow_circle: |   |

### OpenSearch 2.19.0 [exit criteria](https://example.com) status:
Criteria | Status | Description  | Comments
-- | -- | -- | --
All integration tests are passing | :green_circle: |   |

### OpenSearch-Dashboards 2.19.0 [exit criteria](https://example.com) status:
Criteria | Status | Description  | Comments
-- | -- | -- | --
All integration tests are passing | :green_circle: |   |
'''

    @Test
    void testApplyChoreStatusCirclesWritesPerProductStatusToEachExitTable() {
        // Integration tests failed on OpenSearch but passed on OpenSearch-Dashboards; each exit table
        // gets its own product's status, and the entrance owner row gets its 'both' status.
        def statuses = [
            release_owners_assigned      : [both: 'met'],
            all_integration_tests_passing: ['opensearch': 'not_met', 'opensearch-dashboards': 'met']
        ]
        String updated = ReleaseStateData.applyChoreStatusCircles(CIRCLE_BODY, statuses)
        assert updated.contains('an assigned owner | :green_circle:')
        assert updated.contains('All integration tests are passing | :red_circle:')
        assert updated.contains('All integration tests are passing | :green_circle:')
    }

    @Test
    void testApplyChoreStatusCirclesLeavesRowUntouchedWhenProductStatusMissing() {
        // Only the OpenSearch integration status is known; the OSD exit row keeps its existing circle.
        def statuses = [all_integration_tests_passing: ['opensearch': 'not_met']]
        String updated = ReleaseStateData.applyChoreStatusCircles(CIRCLE_BODY, statuses)
        assert updated.contains('All integration tests are passing | :red_circle:')
        assert updated.contains('All integration tests are passing | :green_circle:')
    }

    @Test
    void testApplyChoreStatusCirclesIgnoresRowsOutsideKnownTables() {
        // A chore keyword appearing before any criteria table heading must not be rewritten.
        String body = 'All integration tests are passing | :green_circle: |   |\n'
        def statuses = [all_integration_tests_passing: ['opensearch': 'not_met']]
        assert ReleaseStateData.applyChoreStatusCircles(body, statuses) == body
    }

    @Test
    void testApplyChoreStatusCirclesPreservesBodyWhenNothingChanges() {
        // Statuses already match the body's circles, so it round-trips byte-for-byte.
        def statuses = [
            release_owners_assigned      : [both: 'in_progress'],
            all_integration_tests_passing: ['opensearch': 'met', 'opensearch-dashboards': 'met']
        ]
        assert ReleaseStateData.applyChoreStatusCircles(CIRCLE_BODY, statuses) == CIRCLE_BODY
    }

    // A trimmed release issue body carrying all three criteria tables in their real markdown shape.
    private static final String ISSUE_BODY = '''
## Release OpenSearch and OpenSearch Dashboards 3.8.0

I noticed that a manifest was automatically created in [manifests/3.8.0](/opensearch-project/opensearch-build/tree/main/manifests/3.8.0). Please follow the following checklist to make a release.

<details><summary>How to use this issue</summary>
<p>

## This Release Issue

This issue captures the state of the OpenSearch release, its assignee (Release Manager) is responsible for driving the release. Please contact them or @mention them on this issue for help. There are linked issues on components of the release where individual components can be tracked. For more information check the the [Release Process OpenSearch Guide](https://github.com/opensearch-project/opensearch-build/wiki/Releasing-the-Distribution).

</p>
</details>

Please refer to the following link for the release version dates: [Release Schedule and Maintenance Policy](https://opensearch.org/releases.html).

### [Entrance Criteria](https://github.com/opensearch-project/.github/blob/main/RELEASING.md#entrance-criteria-to-start-release-window)
Criteria | Status | Description  | Comments
-- | -- | -- | --
Each component release issue has an assigned owner | :green_circle: |   |
Documentation draft PRs are up and in tech review for all component changes | :green_circle: |   |
Sanity testing is done for all components | :green_circle: |   |
Code coverage has not decreased (all new code has tests) | :green_circle: |   |
Release notes are ready and available for all components | :green_circle: |   |
Roadmap is up-to-date (information is available to create release highlights) | :yellow_circle: |   |
Release ticket is cut, and there's a forum post announcing the start of the window | :green_circle: |   |
[Any necessary security reviews](https://github.com/opensearch-project/.github/blob/main/RELEASING.md#security-reviews) are complete | :red_circle: |   |

### OpenSearch 3.8.0 [exit criteria](https://github.com/opensearch-project/.github/blob/main/RELEASING.md#exit-criteria-to-close-release-window) status:
Criteria | Status | Description  | Comments
-- | -- | -- | --
Performance tests are run, results are posted to the release ticket and there no unexpected regressions | :green_circle: |   |
No unpatched vulnerabilities of medium or higher severity that have been publicly known for more than 60 days | :green_circle: |   |
Documentation has been fully reviewed and   signed off by the documentation community. | :green_circle: |   |
All integration tests are passing |  :green_circle: |   |
Release blog is ready | :yellow_circle: |   |

### OpenSearch-Dashboards 3.8.0 [exit criteria](https://github.com/opensearch-project/.github/blob/main/RELEASING.md#exit-criteria-to-close-release-window) status:
Criteria | Status | Description  | Comments
-- | -- | -- | --
Documentation has been fully reviewed and   signed off by the documentation community | :green_circle: |   |
No unpatched vulnerabilities of medium or higher severity that have been publicly known for more than 60 days | :green_circle: |   |
All integration tests are passing | :green_circle: |   |
Release blog is ready | :red_circle: |   |

</p>
</details>
'''

    @Test
    void testParseManualCriteriaMapsCirclesToStatuses() {
        def byName = releaseStateData.parseManualCriteria(ISSUE_BODY).collectEntries { [it.name, it] }
        assert byName['sanity_testing_done'].status == 'met'
        assert byName['roadmap_up_to_date'].status == 'in_progress'
        assert byName['security_reviews_complete'].status == 'not_met'
    }

    @Test
    void testParseManualCriteriaAssignsProductFromTable() {
        def criteria = releaseStateData.parseManualCriteria(ISSUE_BODY)
        // Entrance rows apply to both products; the exit tables are per product.
        assert criteria.find { it.name == 'sanity_testing_done' }.product == 'both'
        assert criteria.find { it.name == 'sanity_testing_done' }.type == 'entrance'
        assert criteria.find { it.name == 'performance_tests_posted' }.product == 'opensearch'
        assert criteria.findAll { it.name == 'release_blog_ready' }*.product.sort() == ['opensearch', 'opensearch-dashboards']
    }

    @Test
    void testParseManualCriteriaOnlyReturnsManualRows() {
        // Rows covered by a chore (owners, integration tests) are not manual criteria and are skipped.
        def names = releaseStateData.parseManualCriteria(ISSUE_BODY)*.name as Set
        assert names == ['sanity_testing_done', 'roadmap_up_to_date', 'security_reviews_complete',
                         'performance_tests_posted', 'release_blog_ready'] as Set
    }

    @Test
    void testParseManualCriteriaPerformanceRowIsOpenSearchOnly() {
        // The OSD exit table has no performance row, so performance_tests_posted appears exactly once.
        def performance = releaseStateData.parseManualCriteria(ISSUE_BODY).findAll { it.name == 'performance_tests_posted' }
        assert performance.size() == 1
        assert performance[0].product == 'opensearch'
    }

    @Test
    void testParseManualCriteriaUnrecognisedStatusIsUnknown() {
        String body = '''
### [Entrance Criteria](https://example.com)
Criteria | Status | Description  | Comments
-- | -- | -- | --
Sanity testing is done for all components |  |   |
'''
        def sanity = releaseStateData.parseManualCriteria(body).find { it.name == 'sanity_testing_done' }
        assert sanity.status == 'unknown'
    }

    @Test
    void testParseManualCriteriaReturnsEmptyWhenNoTables() {
        assert releaseStateData.parseManualCriteria('no criteria tables here') == []
    }

    @Test
    void testParseManualCriteriaHandlesLeadingPipeRows() {
        // Pipe-bounded rows (| a | b |) must map the Criteria and Status columns to the same cells
        // as the unbounded style, so the leading empty cell is dropped rather than shifting columns.
        String body = '''
### [Entrance Criteria](https://example.com)
| Criteria | Status | Description | Comments |
| -- | -- | -- | -- |
| Sanity testing is done for all components | :green_circle: | | |
| Roadmap is up-to-date | :red_circle: | | |
'''
        def byName = releaseStateData.parseManualCriteria(body).collectEntries { [it.name, it] }
        assert byName['sanity_testing_done'].status == 'met'
        assert byName['roadmap_up_to_date'].status == 'not_met'
    }

    @Test
    void testParseManualCriteriaMatchesCriteriaCellNotOtherColumns() {
        // A keyword appearing only in the Comments column must not produce a spurious criterion.
        String body = '''
### [Entrance Criteria](https://example.com)
Criteria | Status | Description  | Comments
-- | -- | -- | --
Each component release issue has an assigned owner | :green_circle: |   | sanity testing is done later
'''
        assert releaseStateData.parseManualCriteria(body) == []
    }
}
