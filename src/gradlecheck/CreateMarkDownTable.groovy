/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck

class CreateMarkDownTable {
    String failedTest
    ArrayList<String> tableData
    ArrayList<String> additionalPullRequests

    CreateMarkDownTable(String failedTest, List<Map<String, Object>> tableData, List<String> additionalPullRequests) {
        this.failedTest = failedTest
        this.tableData = tableData
        this.additionalPullRequests = additionalPullRequests
    }

    def createMarkdownTable() {

        def tableHeader = """
## Flaky Test Report for `${this.failedTest}`

Noticed the `${this.failedTest}` has some flaky, failing tests that failed during **post-merge actions**.

### Details

| Git Reference | Merged Pull Request | Build Details | Test Name |
|---------------|----------------------|---------------|-----------|
"""
        def tableRows = this.tableData.collect { row ->
            "| ${row.gitReference} | ${row.pullRequestLink} | ${row.buildDetailLink} | ${row.testNames.join('<br><br>')} |"
        }.join("\n")

        def additionalPRSection = """
\nThe other pull requests, besides those involved in post-merge actions, that contain failing tests with the `${this.failedTest}` class are:

${this.additionalPullRequests.collect { pr -> "- [${pr}](https://github.com/opensearch-project/OpenSearch/pull/${pr})" }.join('\n')}

For more details on the failed tests refer to [OpenSearch Gradle Check Metrics](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/e5e64d40-ed31-11ee-be99-69d1dbc75083) dashboard.
"""

        return tableHeader + tableRows + additionalPRSection
    }

}