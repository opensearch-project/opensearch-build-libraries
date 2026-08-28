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

class TestUpdateReleaseIssue extends BuildPipelineTest {

    private def script
    // Every gh command run, and the last body written to a --body-file.
    private List<String> ghCommands
    private String writtenBody
    // Response the metrics -XGET returns, and the body a gh issue view returns; set per test.
    private String statusHits
    private String issueViewBody

    @Override
    @Before
    void setUp() {
        super.setUp()
        ghCommands = []
        writtenBody = null
        statusHits = '{"hits":{"hits":[]}}'
        issueViewBody = ISSUE_BODY
        binding.setVariable('env', [
            'METRICS_HOST_URL'     : 'metrics.url',
            'AWS_ACCESS_KEY_ID'    : 'abc',
            'AWS_SECRET_ACCESS_KEY': 'xyz',
            'AWS_SESSION_TOKEN'    : 'token'
        ])
        binding.setVariable('METRICS_HOST_ACCOUNT', 'METRICS_HOST_ACCOUNT')
        helper.registerAllowedMethod('withSecrets', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('writeFile', [Map], { Map args -> writtenBody = args.text })
        helper.registerAllowedMethod('error', [String], { String message -> throw new Exception(message) })
        // Route each sh call: the metrics -XGET returns the chore-status hits, gh issue view returns
        // the issue body, and gh issue edit/comment are recorded.
        helper.registerAllowedMethod('sh', [Map], { Map args ->
            String s = args.script
            if (s.contains('curl') && s.contains('-XGET')) {
                return statusHits
            }
            ghCommands.add(s)
            if (s.contains('gh issue view')) {
                return issueViewBody
            }
            return ''
        })
        script = loadScript('vars/updateReleaseIssue.groovy')
    }

    private static final String ISSUE_BODY = '''### [Entrance Criteria](https://example.com)
Criteria | Status | Description  | Comments
-- | -- | -- | --
Each component release issue has an assigned owner | :yellow_circle: |   |
Sanity testing is done for all components | :yellow_circle: |   |'''

    @Test
    void testUpdateCriteriaEditsIssueWithRewrittenBody() {
        statusHits = '{"hits":{"hits":[{"_source":{"criterion_name":"release_owners_assigned","product":"both","status":"met"}}]}}'
        issueViewBody = ISSUE_BODY

        script.call(version: '3.8.0', releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/5152')

        assert ghCommands.any { it.contains('gh issue view 5152') }
        assert ghCommands.any { it.contains('gh issue edit 5152') }
        assert writtenBody.contains('an assigned owner | :green_circle:')
    }

    @Test
    void testUpdateCriteriaWritesBlockingComponentsIntoCommentsCell() {
        statusHits = '{"hits":{"hits":[{"_source":{"criterion_name":"release_owners_assigned","product":"both","status":"not_met","blocking_components":["sql","k-NN"]}}]}}'

        script.call(version: '3.8.0', releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/5152')

        assert writtenBody.contains('an assigned owner | :red_circle:')
        assert writtenBody.contains('<details><summary>OSCAR: blocking components</summary><p>sql, k-NN</p></details>')
    }

    @Test
    void testUpdateCriteriaSanitizesComponentNamesFromTheIndex() {
        // Component names come from the index, so markup and stray pipes are stripped before they reach
        // the issue body: neither can inject HTML nor break the table row.
        statusHits = '{"hits":{"hits":[{"_source":{"criterion_name":"release_owners_assigned","product":"both","status":"not_met","blocking_components":["<b>sql</b>","dashboards|extra","<>"]}}]}}'

        script.call(version: '3.8.0', releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/5152')

        // Angle brackets and pipes are dropped, and a name left empty by the strip ('<>') is skipped.
        assert writtenBody.contains('<p>bsql/b, dashboardsextra</p>')
        assert !writtenBody.contains('<b>')
    }

    @Test
    void testUpdateCriteriaSkipsEditWhenNothingChanged() {
        // Body already reflects the indexed status, so no edit should follow.
        statusHits = '{"hits":{"hits":[{"_source":{"criterion_name":"release_owners_assigned","product":"both","status":"met"}}]}}'
        issueViewBody = '''### [Entrance Criteria](https://example.com)
Criteria | Status | Description  | Comments
-- | -- | -- | --
Each component release issue has an assigned owner | :green_circle: |   |'''

        script.call(version: '3.8.0', releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/5152')

        assert ghCommands.any { it.contains('gh issue view 5152') }
        assert !ghCommands.any { it.contains('gh issue edit') }
    }

    @Test
    void testUpdateCriteriaSkipsWhenNoStatusesIndexed() {
        statusHits = '{"hits":{"hits":[]}}'

        script.call(version: '3.8.0', releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/5152')

        assert ghCommands.isEmpty()
    }

    @Test
    void testCommentActionPostsBodyFile() {
        script.call(
            version: '3.8.0',
            releaseIssue: 'https://github.com/opensearch-project/opensearch-build/issues/5152',
            action: 'comment',
            comment: 'OSCAR recommends GO.'
        )

        assert writtenBody == 'OSCAR recommends GO.'
        assert ghCommands.any { it.contains('gh issue comment 5152') && it.contains('--body-file') }
    }

    @Test
    void testInvalidIssueUrlErrors() {
        try {
            script.call(version: '3.8.0', releaseIssue: 'not-a-url')
            assert false : 'Expected an error for an invalid issue URL'
        } catch (Exception e) {
            assert e.message.contains('not a valid opensearch-build issue URL')
        }
    }
}
