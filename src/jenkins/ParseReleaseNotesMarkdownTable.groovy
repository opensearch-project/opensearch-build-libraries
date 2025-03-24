/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

class ParseReleaseNotesMarkdownTable {
    String markdown

    ParseReleaseNotesMarkdownTable(String markdown) {
        this.markdown = markdown
    }

    def parseReleaseNotesMarkdownTableRows() {
        try {
            def rows = markdown.readLines().findAll { it.startsWith('|') }
            // Skipping headers
            rows = rows[2..-1]
            return rows.collect { row ->
                def cells = row.split("\\|")
                return [
                        'Component': cells[1].trim(),
                        'Branch': cells[2].trim(),
                        'Commit ID': cells[3].trim(),
                        'Commit Date': cells[4].trim(),
                        'Status': cells[5].trim()
                ]
            }
        } catch (Exception ex) {
            error("Unable to parse the release notes markdown table: ${ex.getMessage()}")
        }
    }
}
