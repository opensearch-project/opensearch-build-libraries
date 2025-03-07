/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck.tests

import gradlecheck.ParseMarkDownTable
import org.junit.Test
import static org.junit.Assert.assertEquals

class ParseMarkDownTableTest {

    @Test
    void testParseMarkdownTableRows() {
        def markdown = """
            ## Flaky Test Report for `PitMultiNodeIT`

            Noticed the `PitMultiNodeIT` has some flaky, failing tests that failed during **post-merge actions**.

            ### Details

            | Git Reference | Merged Pull Request | Build Details | Test Name |
            |---------------|----------------------|---------------|-----------|
            | 708c37120bea33f258d1656132b9b05642c92720 | [14502](https://github.com/opensearch-project/OpenSearch/pull/14502) | [41571](https://build.ci.opensearch.org/job/gradle-check/41571/testReport/) | `org.opensearch.search.pit.PitMultiNodeIT.testCreatePitWhileNodeDropWithAllowPartialCreationFalse {p0={"search.concurrent_segment_search.enabled":"false"}}` |

            The other pull requests, besides those involved in post-merge actions, that contain failing tests with the `PitMultiNodeIT` class are:

            - [13801](https://github.com/opensearch-project/OpenSearch/pull/13801)
            - [14362](https://github.com/opensearch-project/OpenSearch/pull/14362)

            For more details on the failed tests refer to [OpenSearch Gradle Check Metrics](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/e5e64d40-ed31-11ee-be99-69d1dbc75083) dashboard.
        """
        ParseMarkDownTable parser = new ParseMarkDownTable(markdown)
        def result = parser.parseMarkdownTableRows()
        assertEquals("Expected 1 row in the result", 1, result.size())
        assertEquals("708c37120bea33f258d1656132b9b05642c92720", result[0]['Git Reference'])
        assertEquals("[14502](https://github.com/opensearch-project/OpenSearch/pull/14502)", result[0]['Merged Pull Request'])
        assertEquals("[41571](https://build.ci.opensearch.org/job/gradle-check/41571/testReport/)", result[0]['Build Details'])
        assertEquals("`org.opensearch.search.pit.PitMultiNodeIT.testCreatePitWhileNodeDropWithAllowPartialCreationFalse {p0={\"search.concurrent_segment_search.enabled\":\"false\"}}`", result[0]['Test Name'])
    }
}
