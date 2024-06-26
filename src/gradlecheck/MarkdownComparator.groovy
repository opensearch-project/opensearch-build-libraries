/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck

class MarkdownComparator {

    ArrayList<String> testReportMarkDownTable
    ArrayList<String> gitHubMarkDownTable

    MarkdownComparator(ArrayList<String> testReportMarkDownTable, ArrayList<String> gitHubMarkDownTable) {
        this.testReportMarkDownTable = testReportMarkDownTable
        this.gitHubMarkDownTable = gitHubMarkDownTable
    }

    def markdownComparison() {
        def differences = testReportMarkDownTable.findAll { ghRow ->
            !gitHubMarkDownTable.any { compRow ->
                ghRow['Git Reference'] == compRow['Git Reference'] &&
                        ghRow['Merged Pull Request'] == compRow['Merged Pull Request'] &&
                        ghRow['Build Details'] == compRow['Build Details'] &&
                        ghRow['Test Name'] == compRow['Test Name']
            }
        }
        return differences
    }
}
