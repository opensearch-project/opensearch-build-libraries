/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck

class ParseMarkDownTable {
    String markdown

    ParseMarkDownTable(String markdown) {
        this.markdown = markdown
    }

    def parseMarkdownTableRows() {
        def rows = markdown.split("\\|\\s*\\n")
        rows = rows[2..-2] // Skipping headers and footer
        return rows.collect { row ->
            def cells = row.split("\\|\\s*")
            return [
                    'Git Reference': cells[1].trim(),
                    'Merged Pull Request': cells[2].trim(),
                    'Build Details': cells[3].trim(),
                    'Test Name': cells[4].trim()
            ]
        }
    }
}