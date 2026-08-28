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
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestCheckUnpatchedVulnerabilities extends BuildPipelineTest {

    // Response for the scans index and the advisories lookup, overridable per test.
    private String scansResponse
    private String advisoriesResponse
    // Captures every query body sent to the cluster, keyed by the index it targeted.
    private List<Map> searches

    @Override
    @Before
    void setUp() {
        super.setUp()
        searches = []
        binding.setVariable('ADVISORIES_HOST_ACCOUNT', 'ADVISORIES_HOST_ACCOUNT')
        binding.setVariable('env', [
                'ADVISORIES_HOST_URL'  : 'sample.url',
                'AWS_ACCESS_KEY_ID'    : 'abc',
                'AWS_SECRET_ACCESS_KEY': 'xyz',
                'AWS_SESSION_TOKEN'    : 'sampleToken'
        ])
        helper.registerAllowedMethod('withSecrets', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })

        // The branch tag is derived from the core component's manifest ref. The job under test is
        // version 3.8.0, whose real manifest ref is 'tags/3.8.0' -> origin/3.8.
        helper.registerAllowedMethod('fileExists', [String], { String path -> return true })
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            return [components: [[name: 'OpenSearch', ref: 'tags/3.8.0']]]
        })

        // Two projects, three open (non-excluded) CVEs; CVE-2 is excluded and must be dropped.
        scansResponse = '''
            {
              "hits": {
                "hits": [
                  {
                    "_source": {
                      "project": {"name": "Alerting"},
                      "vulnerabilities": [
                        {"id": "CVE-1", "excluded": false},
                        {"id": "CVE-2", "excluded": true}
                      ]
                    }
                  },
                  {
                    "_source": {
                      "project": {"name": "SQL"},
                      "vulnerabilities": [
                        {"id": "CVE-3", "excluded": false},
                        {"id": "CVE-4", "excluded": false}
                      ]
                    }
                  }
                ]
              }
            }
        '''
        // Of the open CVEs, CVE-1 and CVE-3 are aged medium-or-higher; CVE-4 is not returned.
        advisoriesResponse = '''
            {
              "hits": {
                "hits": [
                  {"_source": {"aliases": ["CVE-1"]}},
                  {"_source": {"aliases": ["CVE-3"]}}
                ]
              }
            }
        '''

        // Route each cluster search by the index in its URL: the index-less _search resolves the
        // latest scans index, the concrete scans-NNNNNN returns open vulnerabilities, advisories
        // returns aged CVEs, ignored-advisories returns exemptions.
        helper.registerAllowedMethod('sh', [Map.class], { Map args ->
            String s = args.script
            def indexMatcher = (s =~ /sample\.url\/([^\/]*)\/?_search/)
            String index = indexMatcher ? indexMatcher[0][1] : null
            def bodyMatcher = (s =~ /-d "(.*)" \| jq/)
            searches.add([index: index, body: bodyMatcher ? bodyMatcher[0][1] : null])
            if (index == null || index.isEmpty()) {
                return '{"hits":{"hits":[{"_index":"scans-000335"}]}}'
            }
            if (index == 'ignored-advisories') {
                return '{"hits":{"hits":[]}}'
            }
            if (index.startsWith('scans-')) {
                return scansResponse
            }
            return advisoriesResponse
        })
    }

    @Test
    void testReturnsBlockingCvesKeyedByProject() {
        runScript('tests/jenkins/jobs/CheckUnpatchedVulnerabilities_Jenkinsfile')
        // The 60-day window is measured back from the release date (2026-08-15 -> 2026-06-16).
        assertThat(getCommands('echo', 'published on or before'),
                hasItem('Checking unpatched medium-or-higher vulnerabilities for origin/3.8 (published on or before 2026-06-16T23:59:59.999Z).'))
        // CVE-1 -> Alerting, CVE-3 -> SQL; CVE-2 (excluded) and CVE-4 (not aged) are absent.
        assertThat(getCommands('echo', 'older than the 60-day window'),
                hasItem('Unpatched medium-or-higher vulnerabilities older than the 60-day window: [Alerting:[CVE-1], SQL:[CVE-3]]'))
    }

    @Test
    void testReleaseBranchTagIsFilteredOnTheScansSearch() {
        runScript('tests/jenkins/jobs/CheckUnpatchedVulnerabilities_Jenkinsfile')
        def scansSearch = searches.find { it.index?.startsWith('scans-0') }
        assert scansSearch != null
        assert scansSearch.body.contains('project.tag')
        assert scansSearch.body.contains('origin/3.8')
        assert scansSearch.body.contains('bundle_opensearch')
    }

    @Test
    void testBranchTagComesFromManifestRefNotVersionBeforeBranchCut() {
        // Before the release branch is cut the core ref is still 'main', so the scan must be keyed on
        // origin/main — not origin/3.8 derived from the version (the bug this fixes).
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            return [components: [[name: 'OpenSearch', ref: 'main']]]
        })
        runScript('tests/jenkins/jobs/CheckUnpatchedVulnerabilities_Jenkinsfile')
        assertThat(getCommands('echo', 'published on or before'),
                hasItem('Checking unpatched medium-or-higher vulnerabilities for origin/main (published on or before 2026-06-16T23:59:59.999Z).'))
        def scansSearch = searches.find { it.index?.startsWith('scans-0') }
        assert scansSearch.body.contains('origin/main')
    }

    @Test
    void testBranchTagFallsBackToVersionWhenManifestMissing() {
        // No manifest on disk -> derive the tag from the version (3.8.0 -> origin/3.8).
        helper.registerAllowedMethod('fileExists', [String], { String path -> return false })
        runScript('tests/jenkins/jobs/CheckUnpatchedVulnerabilities_Jenkinsfile')
        assertThat(getCommands('echo', 'published on or before'),
                hasItem('Checking unpatched medium-or-higher vulnerabilities for origin/3.8 (published on or before 2026-06-16T23:59:59.999Z).'))
    }

    @Test
    void testBranchTagFromAlreadyPrefixedManifestRef() {
        // A ref already in origin/ form is passed through unchanged.
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            return [components: [[name: 'OpenSearch', ref: 'origin/2.19']]]
        })
        runScript('tests/jenkins/jobs/CheckUnpatchedVulnerabilities_Jenkinsfile')
        assertThat(getCommands('echo', 'published on or before'),
                hasItem('Checking unpatched medium-or-higher vulnerabilities for origin/2.19 (published on or before 2026-06-16T23:59:59.999Z).'))
    }

    @Test
    void testBranchTagFromNonNumericManifestRef() {
        // A non-version ref is prefixed rather than reduced to major.minor.
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            return [components: [[name: 'OpenSearch', ref: 'feature-branch']]]
        })
        runScript('tests/jenkins/jobs/CheckUnpatchedVulnerabilities_Jenkinsfile')
        assertThat(getCommands('echo', 'published on or before'),
                hasItem('Checking unpatched medium-or-higher vulnerabilities for origin/feature-branch (published on or before 2026-06-16T23:59:59.999Z).'))
    }

    @Test
    void testCriterionMetWhenNoAgedAdvisories() {
        // No advisory is aged medium-or-higher, so the criterion is met (empty map).
        advisoriesResponse = '{"hits":{"hits":[]}}'
        runScript('tests/jenkins/jobs/CheckUnpatchedVulnerabilities_Jenkinsfile')
        assertThat(getCommands('echo', 'No unpatched'),
                hasItem('No unpatched medium-or-higher vulnerabilities older than the 60-day window.'))
    }

    def getCommands(String methodName, String searchString) {
        def matches = []
        helper.callStack.findAll { call -> call.methodName == methodName }.each { call ->
            def args = callArgsToString(call)
            if (args.contains(searchString)) {
                matches.add(args)
            }
        }
        return matches
    }
}
