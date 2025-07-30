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
        def groupRows = this.tableData.findAll { it.gitReference && !["2.19", "2.x", "1.x"].contains(it.gitReference)}.groupBy{ it.gitReference }
        def tableRows = groupRows.collect { Ref, rows ->
            def pullRequestLink = rows.collect { it.pullRequestLink }.unique().join('<br><br>')
            def buildDetailLink = rows.collect { it.buildDetailLink }.unique().join('<br><br>')
            def testNames = rows.collectMany { it.testNames }.unique().join('<br><br>')

            "| ${Ref} | ${pullRequestLink} | ${buildDetailLink} | ${testNames} |"
        }.join("\n")


        def tableHeader = """   
## Flaky Test Report for `${this.failedTest}`

Noticed the `${this.failedTest}` has some flaky, failing tests that failed during **post-merge actions** and **timer trigerred run on main branch**.

### Details

| Git Reference | Merged Pull Request | Build Details | Test Name |
|---------------|----------------------|---------------|-----------|
"""

        def additionalPRSection = """
\nThe other pull requests, besides those involved in post-merge actions, that contain failing tests with the `${this.failedTest}` class are:

${this.additionalPullRequests.collect { pr -> "- [${pr}](https://github.com/opensearch-project/OpenSearch/pull/${pr})" }.join('\n')}

For more details on the failed tests refer to [OpenSearch Gradle Check Metrics](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/e5e64d40-ed31-11ee-be99-69d1dbc75083) dashboard.
"""
        return tableHeader + tableRows + additionalPRSection
    }

}
