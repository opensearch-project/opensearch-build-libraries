/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck.tests

import gradlecheck.CreateMarkDownTable
import org.junit.Test


class CreateMarkDownTableTest  {

    @Test
    void testCreateMarkdownTableWithSampleData() {
        def failedTest = "ExampleTest"
        def tableData = [
                [gitReference: "abc123", pullRequestLink: "https://github.com/opensearch-project/OpenSearch/pull/1", buildDetailLink: "https://ci.opensearch.org/1", testNames: ["test1", "test2"]],
                [gitReference: "def456", pullRequestLink: "https://github.com/opensearch-project/OpenSearch/pull/2", buildDetailLink: "https://ci.opensearch.org/2", testNames: ["test3"]]
        ]
        def additionalPullRequests = ["3", "4"]
        def createMarkDownTable = new CreateMarkDownTable(failedTest, tableData, additionalPullRequests)
        def result = createMarkDownTable.createMarkdownTable()
        def expectedOutput = """
## Flaky Test Report for `ExampleTest`

Noticed the `ExampleTest` has some flaky, failing tests that failed during **post-merge actions**.

### Details

| Git Reference | Merged Pull Request | Build Details | Test Name |
|---------------|----------------------|---------------|-----------|
| abc123 | https://github.com/opensearch-project/OpenSearch/pull/1 | https://ci.opensearch.org/1 | test1<br><br>test2 |
| def456 | https://github.com/opensearch-project/OpenSearch/pull/2 | https://ci.opensearch.org/2 | test3 |
\nThe other pull requests, besides those involved in post-merge actions, that contain failing tests with the `ExampleTest` class are:

- [3](https://github.com/opensearch-project/OpenSearch/pull/3)
- [4](https://github.com/opensearch-project/OpenSearch/pull/4)

For more details on the failed tests refer to [OpenSearch Gradle Check Metrics](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/e5e64d40-ed31-11ee-be99-69d1dbc75083) dashboard.
"""
        assert result == expectedOutput
    }
}
