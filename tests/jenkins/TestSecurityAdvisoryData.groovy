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
import jenkins.SecurityAdvisoryData
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class TestSecurityAdvisoryData {
    private final String advisoriesUrl = 'http://example.com'
    private final String awsAccessKey = 'testAccessKey'
    private final String awsSecretKey = 'testSecretKey'
    private final String awsSessionToken = 'testSessionToken'

    private SecurityAdvisoryData advisoryData
    private def script
    // Each search's target index in call order, and the queued responses to hand back in call order.
    private List<String> searchedIndices
    private List<String> queryBodies
    private List<String> responses
    private String ignoredAdvisoriesResponse

    @Before
    void setUp() {
        searchedIndices = []
        queryBodies = []
        responses = []
        ignoredAdvisoriesResponse = '{"hits":{"hits":[]}}'
        script = new Expando()
        script.println = { msg -> }
        script.sh = { Map args ->
            String s = args.script
            def matcher = (s =~ /${advisoriesUrl}\/([^\/]*)\/?_search/)
            String index = matcher ? matcher[0][1] : null
            searchedIndices.add(index)
            def bodyMatcher = (s =~ /-d "(.*)" \| jq/)
            queryBodies.add(bodyMatcher ? bodyMatcher[0][1] : null)
            if (index == SecurityAdvisoryData.IGNORED_ADVISORIES_INDEX) {
                return ignoredAdvisoriesResponse
            }
            return responses ? responses.remove(0) : '{"hits":{"hits":[]}}'
        }
        advisoryData = new SecurityAdvisoryData(advisoriesUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    @Test
    void testResolveVersionTagMapsVersionsToBranchTags() {
        assertEquals('origin/3.8', SecurityAdvisoryData.resolveVersionTag('3.8.0'))
        assertEquals('origin/3.8', SecurityAdvisoryData.resolveVersionTag('3.8'))
        assertEquals('origin/main', SecurityAdvisoryData.resolveVersionTag('main'))
        assertEquals('origin/main', SecurityAdvisoryData.resolveVersionTag('latest'))
        assertEquals('origin/main', SecurityAdvisoryData.resolveVersionTag('LATEST'))
        // An already-resolved tag is passed through unchanged.
        assertEquals('origin/2.19', SecurityAdvisoryData.resolveVersionTag('origin/2.19'))
        // Non-numeric / unrecognised values pass through rather than being mangled.
        assertEquals('some-branch', SecurityAdvisoryData.resolveVersionTag('some-branch'))
        assertEquals(null, SecurityAdvisoryData.resolveVersionTag(null))
    }

    @Test
    void testGetLatestScansIndexReturnsHighestNumberedIndex() {
        // The cluster-wide search sorts _index desc, so the first hit is the newest scans index.
        responses = ['{"hits":{"hits":[{"_index":"scans-000335"}]}}']
        assertEquals('scans-000335', advisoryData.getLatestScansIndex())
        // Resolved via a cluster-wide search (no index prefix in the URL).
        assertEquals('', searchedIndices[0])
    }

    @Test
    void testGetLatestScansIndexThrowsWhenNoScanDocuments() {
        responses = ['{"hits":{"hits":[]}}']
        try {
            advisoryData.getLatestScansIndex()
            fail('Expected RuntimeException when no scan documents exist')
        } catch (RuntimeException e) {
            assertTrue(e.message.contains('Could not resolve latest scans index'))
        }
    }

    @Test
    void testGetOpenVulnerabilitiesByProjectKeysOpenCvesByProjectName() {
        responses = ['''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "project": {"name": "Alerting"},
                      "vulnerabilities": [
                        {"id": "CVE-1", "excluded": false},
                        {"id": "CVE-2", "excluded": false}
                      ]
                    }
                  },
                  {
                    "_source": {
                      "project": {"name": "SQL"},
                      "vulnerabilities": [
                        {"id": "CVE-3", "excluded": false}
                      ]
                    }
                  }
                ]
              }
            }
        ''']
        def byProject = advisoryData.getOpenVulnerabilitiesByProject('scans-000335', 'origin/3.8')
        assertEquals(['CVE-1', 'CVE-2'], byProject['Alerting'])
        assertEquals(['CVE-3'], byProject['SQL'])
        // The scans index is searched, and the branch tag is filtered on project.tag.
        int scansSearch = searchedIndices.indexOf('scans-000335')
        assertTrue(scansSearch >= 0)
        assertTrue(queryBodies[scansSearch].contains('project.tag'))
        assertTrue(queryBodies[scansSearch].contains('origin/3.8'))
        assertTrue(queryBodies[scansSearch].contains('timestamp.commit'))
        assertTrue(queryBodies[scansSearch].contains('release_type.keyword'))
        assertTrue(queryBodies[scansSearch].contains('bundle'))
    }

    @Test
    void testGetOpenVulnerabilitiesByProjectCollectsAliasesAlongsideId() {
        responses = ['''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "project": {"name": "Alerting"},
                      "vulnerabilities": [
                        {"id": "CVE-1", "aliases": ["GHSA-xxxx-yyyy-zzzz"], "excluded": false}
                      ]
                    }
                  }
                ]
              }
            }
        ''']
        def byProject = advisoryData.getOpenVulnerabilitiesByProject('scans-000335', 'origin/3.8')
        assertEquals(['CVE-1', 'GHSA-xxxx-yyyy-zzzz'], byProject['Alerting'])
    }

    @Test
    void testGetOpenVulnerabilitiesByProjectHonorsLiveExemptionsForItsOwnProject() {
        // Alerting has a live exemption for CVE-1 (added after the last scan, so still flagged in the
        // scan); SQL shares that exemption alias but it must not suppress SQL's own CVE-1.
        ignoredAdvisoriesResponse = '''
            {
              "hits": {
                "hits": [
                  {"_source": {"project": "Alerting", "tag": "origin/3.8", "aliases": ["CVE-1"]}}
                ]
              }
            }
        '''
        responses = ['''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "project": {"name": "Alerting"},
                      "vulnerabilities": [
                        {"id": "CVE-1", "excluded": false},
                        {"id": "CVE-2", "excluded": false}
                      ]
                    }
                  },
                  {
                    "_source": {
                      "project": {"name": "SQL"},
                      "vulnerabilities": [
                        {"id": "CVE-1", "excluded": false}
                      ]
                    }
                  }
                ]
              }
            }
        ''']
        def byProject = advisoryData.getOpenVulnerabilitiesByProject('scans-000335', 'origin/3.8')
        assertEquals(['CVE-2'], byProject['Alerting'])
        assertEquals(['CVE-1'], byProject['SQL'])
    }

    @Test
    void testGetOpenVulnerabilitiesByProjectExcludesExcludedCvesAndDedupes() {
        // Excluded CVEs are dropped; the same open CVE listed across sub-components is deduped to one.
        responses = ['''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "project": {"name": "Alerting"},
                      "vulnerabilities": [
                        {"id": "CVE-1", "excluded": false},
                        {"id": "CVE-1", "excluded": false},
                        {"id": "CVE-2", "excluded": true}
                      ]
                    }
                  }
                ]
              }
            }
        ''']
        def byProject = advisoryData.getOpenVulnerabilitiesByProject('scans-000335', 'origin/3.8')
        assertEquals(['CVE-1'], byProject['Alerting'])
    }

    @Test
    void testGetOpenVulnerabilitiesByProjectSkipsHitsWithoutProjectNameAndVulnsWithoutId() {
        // A scan hit with no project.name is skipped, and a vulnerability with no id is ignored
        // (only the well-formed CVE on the named project survives).
        responses = ['''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "vulnerabilities": [
                        {"id": "CVE-9", "excluded": false}
                      ]
                    }
                  },
                  {
                    "_source": {
                      "project": {"name": "Alerting"},
                      "vulnerabilities": [
                        {"excluded": false},
                        {"id": "CVE-1", "excluded": false}
                      ]
                    }
                  }
                ]
              }
            }
        ''']
        def byProject = advisoryData.getOpenVulnerabilitiesByProject('scans-000335', 'origin/3.8')
        assertEquals(['Alerting'], byProject.keySet().toList())
        assertEquals(['CVE-1'], byProject['Alerting'])
    }

    @Test
    void testGetOpenVulnerabilitiesByProjectOmitsProjectsWithNoOpenCves() {
        // A project whose only vulnerability is excluded is left out of the map entirely.
        responses = ['''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "project": {"name": "Alerting"},
                      "vulnerabilities": [
                        {"id": "CVE-2", "excluded": true}
                      ]
                    }
                  }
                ]
              }
            }
        ''']
        def byProject = advisoryData.getOpenVulnerabilitiesByProject('scans-000335', 'origin/3.8')
        assertEquals([:], byProject)
    }

    @Test
    void testGetAgedMediumOrHigherAdvisoriesReturnsSortedIntersection() {
        // Of the queried ids, the advisories index reports two as aged medium-or-higher; the third
        // is neither aged nor severe, so it is not in the returned hits and is excluded.
        responses = ['''
            {
              "hits": {
                "hits": [
                  {"_source": {"aliases": ["CVE-2"]}},
                  {"_source": {"aliases": ["CVE-1"]}}
                ]
              }
            }
        ''']
        def aged = advisoryData.getAgedMediumOrHigherAdvisories(['CVE-1', 'CVE-2', 'CVE-3'], '2026-06-01T23:59:59.999Z')
        assertEquals(['CVE-1', 'CVE-2'], aged)
        assertEquals('advisories', searchedIndices[0])
        // The severity and publish-age filters are applied query-side.
        assertTrue(queryBodies[0].contains('severity'))
        assertTrue(queryBodies[0].contains('timestamp.publish'))
        assertTrue(queryBodies[0].contains('2026-06-01T23:59:59.999Z'))
    }

    @Test
    void testGetAgedMediumOrHigherAdvisoriesIgnoresAliasesOutsideTheQueriedBatch() {
        // An advisory hit can carry extra aliases (e.g. GHSA ids) alongside the CVE we asked about;
        // only aliases actually in the queried batch are kept, the rest are ignored.
        responses = ['''
            {
              "hits": {
                "hits": [
                  {"_source": {"aliases": ["CVE-1", "GHSA-xxxx-yyyy-zzzz"]}}
                ]
              }
            }
        ''']
        def aged = advisoryData.getAgedMediumOrHigherAdvisories(['CVE-1'], '2026-06-01T23:59:59.999Z')
        assertEquals(['CVE-1'], aged)
    }

    @Test
    void testGetAgedMediumOrHigherAdvisoriesReturnsEmptyForNoInput() {
        // No open CVEs means no lookup is issued and the criterion is met.
        assertEquals([], advisoryData.getAgedMediumOrHigherAdvisories([], '2026-06-01T23:59:59.999Z'))
        assertTrue(searchedIndices.isEmpty())
    }

    @Test
    void testGetAgedMediumOrHigherAdvisoriesBatchesLargeCveSets() {
        // Above the batch size the lookup is split into multiple advisories searches; every batch's
        // matches are unioned. 1001 ids -> two batches (1000 + 1).
        def cveIds = (1..1001).collect { "CVE-${it}".toString() }
        responses = [
            '{"hits":{"hits":[{"_source":{"aliases":["CVE-1"]}}]}}',
            '{"hits":{"hits":[{"_source":{"aliases":["CVE-1001"]}}]}}'
        ]
        def aged = advisoryData.getAgedMediumOrHigherAdvisories(cveIds, '2026-06-01T23:59:59.999Z')
        assertEquals(['CVE-1', 'CVE-1001'], aged)
        assertEquals(2, searchedIndices.size())
        assertEquals(['advisories', 'advisories'], searchedIndices)
    }
}
