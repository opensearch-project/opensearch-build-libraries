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
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.MatcherAssert.assertThat

/**
 * Tests for the indexReleaseState var.
 *
 * The var orchestrates: read active releases from the schedule index (a curl via sh), run each
 * automated chore check, normalize its result, and index one criterion doc per check; then parse
 * the release issue's manual-criteria tables and index one doc per manual row.
 *
 * The chore vars are stubbed by name via registerAllowedMethod so each chore's OUTCOME can be
 * injected directly, and the indexed documents are captured from the writeFile payloads (every
 * indexDocument writes the body to a temp file before the POST). Asserting on those payloads
 * exercises statusOf, indexCriterion, normalizeResult, the per-check render closures, and
 * indexManualCriteriaForRelease end to end.
 */
class TestIndexReleaseState extends BuildPipelineTest {

    // The active-releases search response returned by the schedule-index curl (a single active
    // release with a release issue so the manual-criteria path also runs).
    private static final String ACTIVE_RELEASES_RESPONSE = '''
        {
          "hits": {
            "hits": [
              {
                "_source": {
                  "version": "3.9.0",
                  "release_date": "2026-09-15",
                  "release_issue": "https://github.com/opensearch-project/opensearch-build/issues/6062"
                }
              }
            ]
          }
        }
    '''

    // A minimal release issue body with one manual criterion per table, each with a status circle,
    // so parseManualCriteria yields entrance-both, exit-opensearch, exit-osd manual rows.
    private static final String ISSUE_BODY = '''\
## Entrance Criteria
| Criteria | Status | Description |
| --- | --- | --- |
| Security reviews are complete | :green_circle: | done |

## OpenSearch 3.9.0 [Exit Criteria]
| Criteria | Status | Description |
| --- | --- | --- |
| Performance tests are run | :yellow_circle: | in progress |

## OpenSearch Dashboards 3.9.0 [Exit Criteria]
| Criteria | Status | Description |
| --- | --- | --- |
| Release blog is ready | :red_circle: | not started |
'''

    // Captured index documents (parsed JSON), one per writeFile call.
    private List<Map> indexedDocs

    // Chore outcomes for the current test; keyed by var name. Overridable per test before running.
    private Map choreOutcomes

    // Chore var names that should throw when invoked, to exercise the runCheck -> 'unknown' path.
    private Set choreThrows

    @Override
    @Before
    void setUp() {
        super.setUp()
        indexedDocs = []
        choreThrows = [] as Set
        binding.setVariable('METRICS_HOST_ACCOUNT', 'METRICS_HOST_ACCOUNT')
        binding.setVariable('env', [
                'METRICS_HOST_URL'     : 'sample.url',
                'AWS_ACCESS_KEY_ID'    : 'abc',
                'AWS_SECRET_ACCESS_KEY': 'xyz',
                'AWS_SESSION_TOKEN'    : 'sampleToken',
                'JOB_NAME'             : 'release-state',
                'BUILD_NUMBER'         : '7'
        ])
        binding.setVariable('GITHUB_USER', 'GITHUB_USER')
        binding.setVariable('GITHUB_TOKEN', 'GITHUB_TOKEN')
        helper.registerAllowedMethod('withSecrets', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })

        // Default chore outcomes: all checks "met" (empty problems). Individual tests override.
        choreOutcomes = [
            checkRequestAssignReleaseOwners : [],
            checkDocumentationIssues        : [],
            checkCodeCoverage               : [],
            checkReleaseNotes               : [],
            checkReleaseIssues              : [],
            checkDocumentationPullRequests  : [],
            checkIntegTestResultsOverview   : ['opensearch': ['linux_x64': []], 'opensearch-dashboards': ['linux_x64': []]],
            checkUnpatchedVulnerabilities   : [:]
        ]

        // The active-releases read is a curl (XGET) via sh returning stdout; the manual-criteria
        // read is a `gh issue view` via sh; every index write is a POST curl returning an HTTP code.
        // Dispatch on the script text.
        helper.registerAllowedMethod('sh', [Map], { Map callArgs ->
            String script = callArgs.script ?: ''
            if (script.contains('_search')) {
                return ACTIVE_RELEASES_RESPONSE
            }
            if (script.contains('gh issue view')) {
                return ISSUE_BODY
            }
            return '201'
        })

        // Every indexed document is written to a temp file before the POST; capture and parse it.
        helper.registerAllowedMethod('writeFile', [Map], { Map callArgs ->
            indexedDocs.add(new JsonSlurperClassic().parseText(callArgs.text))
            return null
        })
    }

    /**
     * Loads the job, then overrides each chore var before running.
     *
     * A chore call from indexReleaseState (checkCodeCoverage(...)) dispatches through the framework's
     * allowed-method registry, not the binding. loadScript's setGlobalVars registers every loaded
     * library global (including the real chore vars) as allowed methods, overwriting any override
     * registered in setUp. Re-registering the chores AFTER load but BEFORE run is the only point at
     * which the injected outcomes win. Each override returns choreOutcomes[name] (read at call time,
     * so a test can set the outcome before calling this), or throws when the name is present in
     * choreThrows to exercise the runCheck -> 'unknown' path.
     */
    private void runIndexReleaseState(criteria = null) {
        // The job wrapper reads CRITERIA from the binding and passes it as args.criteria (null = all).
        binding.setVariable('CRITERIA', criteria)
        def script = loadScript('tests/jenkins/jobs/IndexReleaseState_Jenkinsfile')
        ['checkRequestAssignReleaseOwners', 'checkDocumentationIssues', 'checkCodeCoverage',
         'checkReleaseNotes', 'checkReleaseIssues', 'checkDocumentationPullRequests',
         'checkIntegTestResultsOverview', 'checkUnpatchedVulnerabilities'].each { String name ->
            helper.registerAllowedMethod(name, [Map], { Map callArgs ->
                if (choreThrows.contains(name)) {
                    throw new RuntimeException('boom')
                }
                return choreOutcomes[name]
            })
        }
        runScript(script)
    }

    private List<Map> criterionDocs(String name) {
        return indexedDocs.findAll { it.criterion_name == name }
    }

    private Map criterionDoc(String name, String product) {
        return indexedDocs.find { it.criterion_name == name && it.product == product }
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

    @Test
    void testAllChecksMetIndexGreenAndManualCriteria() {
        runIndexReleaseState()

        // 10 chore criteria docs (8 unique names; integration tests and vulnerabilities are each
        // indexed once per product) + 3 manual criteria docs.
        assert indexedDocs.findAll { it.source == 'chore_check' }.size() == 10
        assert indexedDocs.findAll { it.source == 'issue_table' }.size() == 3

        // Every chore check returned no blocking components -> met.
        assert criterionDocs('code_coverage_not_decreased').every { it.status == 'met' }
        assert criterionDoc('release_owners_assigned', 'both').status == 'met'

        // Manual rows carry the circle-mapped status, product from their table, source issue_table.
        assert criterionDoc('security_reviews_complete', 'both').status == 'met'
        assert criterionDoc('performance_tests_posted', 'opensearch').status == 'in_progress'
        assert criterionDoc('release_blog_ready', 'opensearch-dashboards').status == 'not_met'
    }

    @Test
    void testProgressLoggingPerCheck() {
        runIndexReleaseState()
        assertThat(getCommands('echo', 'active release'),
                hasItem('Found 1 active release(s) in the schedule index.'))
        assertThat(getCommands('echo', 'Running check'),
                hasItem("Running check 'code_coverage_not_decreased [both]' for version 3.9.0."))
        assertThat(getCommands('echo', 'recorded as'),
                hasItem("Check 'code_coverage_not_decreased [both]' for version 3.9.0 recorded as 'met'."))
    }

    @Test
    void testNotMetWhenChoreReturnsBlockingComponents() {
        choreOutcomes.checkCodeCoverage = ['k-NN', 'SQL']
        runIndexReleaseState()

        Map doc = criterionDoc('code_coverage_not_decreased', 'both')
        assert doc.status == 'not_met'
        assert doc.blocking_components == ['k-NN', 'SQL']
        assertThat(getCommands('echo', 'recorded as'),
                hasItem("Check 'code_coverage_not_decreased [both]' for version 3.9.0 recorded as 'not_met'."))
    }

    @Test
    void testUnknownWhenChoreThrows() {
        choreThrows = ['checkReleaseNotes'] as Set
        runIndexReleaseState()

        assert criterionDoc('release_notes_ready', 'both').status == 'unknown'
        assertThat(getCommands('echo', 'failed to run'),
                hasItem("Check 'release_notes_ready [both]' failed to run: boom. Recording status as unknown."))
    }

    @Test
    void testVulnerabilitiesRenderBlockingComponentsAndDetails() {
        choreOutcomes.checkUnpatchedVulnerabilities = ['SQL': ['CVE-1', 'CVE-2'], 'Alerting': ['CVE-3']]
        runIndexReleaseState()

        // no_unpatched_vulnerabilities is indexed once per product; the stub returns the same map for both.
        Map doc = criterionDoc('no_unpatched_vulnerabilities', 'opensearch')
        assert doc.status == 'not_met'
        assert doc.blocking_components.sort() == ['Alerting', 'SQL']
        assert doc.details.contains('SQL: CVE-1, CVE-2')
        assert doc.details.contains('Alerting: CVE-3')
    }

    @Test
    void testIntegResultsRenderFailingComponents() {
        choreOutcomes.checkIntegTestResultsOverview = [
            'opensearch'           : ['linux_x64': ['sql'], 'linux_arm64': []],
            'opensearch-dashboards': ['linux_x64': []]
        ]
        runIndexReleaseState()

        Map os = criterionDoc('all_integration_tests_passing', 'opensearch')
        assert os.status == 'not_met'
        assert os.blocking_components == ['sql']

        Map osd = criterionDoc('all_integration_tests_passing', 'opensearch-dashboards')
        assert osd.status == 'met'
    }

    @Test
    void testNoActiveReleasesShortCircuits() {
        helper.registerAllowedMethod('sh', [Map], { Map callArgs ->
            String script = callArgs.script ?: ''
            if (script.contains('_search')) {
                return '{"hits": {"hits": []}}'
            }
            return '201'
        })
        runIndexReleaseState()

        assert indexedDocs.isEmpty()
        assertThat(getCommands('echo', 'No active releases'),
                hasItem('No active releases to index state for.'))
    }

    @Test
    void testManualCriteriaSkippedWhenNoReleaseIssue() {
        helper.registerAllowedMethod('sh', [Map], { Map callArgs ->
            String script = callArgs.script ?: ''
            if (script.contains('_search')) {
                return '{"hits": {"hits": [{"_source": {"version": "3.9.0", "release_date": "2026-09-15"}}]}}'
            }
            return '201'
        })
        runIndexReleaseState()

        // Chore docs still indexed; no manual (issue_table) docs.
        assert indexedDocs.findAll { it.source == 'chore_check' }.size() == 10
        assert indexedDocs.findAll { it.source == 'issue_table' }.isEmpty()
        assertThat(getCommands('echo', 'skipping manual criteria'),
                hasItem('No release issue for version 3.9.0; skipping manual criteria.'))
    }

    @Test
    void testManualCriteriaSkippedWhenReleaseIssueUrlInvalid() {
        helper.registerAllowedMethod('sh', [Map], { Map callArgs ->
            String script = callArgs.script ?: ''
            if (script.contains('_search')) {
                return '{"hits": {"hits": [{"_source": {"version": "3.9.0", "release_date": "2026-09-15", "release_issue": "not-a-url"}}]}}'
            }
            return '201'
        })
        runIndexReleaseState()

        // An invalid issue URL never reaches gh; manual criteria are skipped.
        assert getCommands('sh', 'gh issue view').isEmpty()
        assert indexedDocs.findAll { it.source == 'issue_table' }.isEmpty()
        assertThat(getCommands('echo', 'not a valid issue URL'),
                hasItem("Release issue 'not-a-url' for version 3.9.0 is not a valid issue URL; skipping manual criteria."))
    }

    @Test
    void testCriteriaFilterIndexesOnlyRequestedChoreCriterion() {
        runIndexReleaseState('code_coverage_not_decreased')

        // Only the requested chore criterion is indexed; nothing else runs.
        assert indexedDocs.size() == 1
        assert indexedDocs[0].criterion_name == 'code_coverage_not_decreased'
        assert indexedDocs[0].source == 'chore_check'
        assertThat(getCommands('echo', 'Restricting to criteria'),
                hasItem('Restricting to criteria: code_coverage_not_decreased.'))
        // A skipped check never logs a "Running check" line.
        assert getCommands('echo', "Running check 'release_owners_assigned").isEmpty()
    }

    @Test
    void testCriteriaFilterAsListRunsBothProductsOfAName() {
        // Requesting a per-product criterion by name re-runs both products for it.
        runIndexReleaseState(['all_integration_tests_passing'])

        def docs = criterionDocs('all_integration_tests_passing')
        assert docs.size() == 2
        assert docs.collect { it.product }.sort() == ['opensearch', 'opensearch-dashboards']
        // No other criteria and no manual docs.
        assert indexedDocs.size() == 2
    }

    @Test
    void testCriteriaFilterSelectsManualCriterionAndSkipsChores() {
        runIndexReleaseState('security_reviews_complete')

        // Only the one manual criterion is indexed; no chore docs.
        assert indexedDocs.findAll { it.source == 'chore_check' }.isEmpty()
        def docs = indexedDocs.findAll { it.source == 'issue_table' }
        assert docs.size() == 1
        assert docs[0].criterion_name == 'security_reviews_complete'
        assert docs[0].status == 'met'
    }

    @Test
    void testCriteriaFilterOfOnlyChoreNamesSkipsIssueFetch() {
        runIndexReleaseState('code_coverage_not_decreased')

        // A filter that names no manual criteria skips the GitHub issue fetch entirely.
        assert getCommands('sh', 'gh issue view').isEmpty()
        assertThat(getCommands('echo', 'No manual criteria requested'),
                hasItem('No manual criteria requested for version 3.9.0; skipping manual criteria.'))
    }

    @Test
    void testCommaSeparatedCriteriaStringMixesChoreAndManual() {
        runIndexReleaseState('code_coverage_not_decreased, security_reviews_complete')

        assert criterionDoc('code_coverage_not_decreased', 'both').source == 'chore_check'
        assert criterionDoc('security_reviews_complete', 'both').source == 'issue_table'
        assert indexedDocs.size() == 2
    }

    @Test
    void testUnknownCriterionNameAbortsBeforeIndexing() {
        String message = null
        helper.registerAllowedMethod('error', [String], { String m ->
            message = m
            throw new RuntimeException(m)
        })
        try {
            runIndexReleaseState('not_a_real_criterion')
        } catch (ignored) {
            // error() aborts the pipeline; the wrapping exception is expected.
        }
        assert message?.contains('Unknown criterion name(s): not_a_real_criterion')
        assert indexedDocs.isEmpty()
    }
}
