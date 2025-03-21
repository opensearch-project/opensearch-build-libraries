/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import jenkins.ParseReleaseNotesMarkdownTable
import org.junit.Test
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class TestParseReleaseNotesMarkdownTable {
    // Helper method to create a testable subclass that overrides error()
    private ParseReleaseNotesMarkdownTable createParser(String markdown) {
        return new ParseReleaseNotesMarkdownTable(markdown) {
            def error(String message) {
                throw new Exception(message)
            }
        }
    }

    @Test
    void testParseMarkdownTableRows() {
        def markdown = """
# Core Components CommitID(after 2025-02-11) & Release Notes info
|              Repo              |Branch|CommitID|Commit Date|Release Notes Exists|
|--------------------------------|------|--------|-----------|--------------------|
|OpenSearch                      |[main]|6c0a95b9|2025-03-17 |False               |
|OpenSearch-Dashboards           |[main]|29f05d7 |2025-03-17 |False               |
|alerting                        |[main]|d6c838b |2025-03-14 |True                |
|alertingDashboards              |[main]|d32321d |2025-03-14 |True                |
|anomaly-detection               |[main]|41db8c0 |2025-03-06 |True                |
|anomalyDetectionDashboards      |[main]|262c16d |2025-03-13 |True                |
|assistantDashboards             |[main]|c7d1c37 |2025-03-17 |True                |
|asynchronous-search             |[main]|e1cea9c |2025-02-20 |False               |
|common-utils                    |[main]|c9c0747 |2025-03-10 |True                |
|cross-cluster-replication       |[main]|ca5bbd4 |2025-03-15 |True                |
"""
        ParseReleaseNotesMarkdownTable parser = new ParseReleaseNotesMarkdownTable(markdown)
        def result = parser.parseReleaseNotesMarkdownTableRows()
        assertEquals("Expected 10 row in the result", 10, result.size())
        assertEquals("OpenSearch", result[0]['Component'])
        assertEquals("[main]", result[0]['Branch'])
        assertEquals("6c0a95b9", result[0]['Commit ID'])
        assertEquals("2025-03-17", result[0]['Commit Date'])
        assertEquals("False", result[0]['Status'])
    }

    @Test
    void testInvalidMarkdownParsing() {
        def invalidMarkdown = "This is not a valid markdown table"
        def parser = createParser(invalidMarkdown)

        try {
            parser.parseReleaseNotesMarkdownTableRows()
            fail("Expected an exception to be thrown")
        } catch (Exception e) {
            assertTrue(e.getMessage().startsWith("Unable to parse the release notes markdown table:"))
        }
    }
}
