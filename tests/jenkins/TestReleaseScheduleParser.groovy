/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.Test
import jenkins.ReleaseScheduleParser

class TestReleaseScheduleParser {

    // Mirrors the real opensearch.org/releases.html schedule table, including revised (struck-through)
    // dates, a row with two release managers, and a row with empty manager / tracking-issue cells.
    private static final String SAMPLE_HTML = '''
<html><body>
<table class="desktop-release-schedule-table">
<tr>
<th>Release Number</th>
<th>First RC Generated (release window opens)</th>
<th>Latest Possible Release Date (release window closes)</th>
<th>Release Manager</th>
<th>Tracking Issue</th>
</tr>
<tr>
<td>3.5.0</td>
<td>January 27th, 2026</td>
<td>February 10th, 2026</td>
<td><a href="https://github.com/foo">Foo</a></td>
<td><a href="https://github.com/opensearch-project/opensearch-build/issues/5897">5897</a></td>
</tr>
<tr>
<td>2.19.6</td>
<td>June 23rd, 2026</td>
<td><strike>June 30th, 2026</strike> July 2nd, 2026</td>
<td><a href="https://github.com/bar/">Bar</a></td>
<td><a href="https://github.com/opensearch-project/opensearch-build/issues/6050">6050</a></td>
</tr>
<tr>
<td>3.8.0</td>
<td><s>July 14th, 2026</s> July 21st, 2026</td>
<td>August 4th, 2026</td>
<td><a href="https://github.com/alice">Alice</a>, <a href="https://github.com/bob">Bob</a></td>
<td><a href="https://github.com/opensearch-project/opensearch-build/issues/6278">6278</a></td>
</tr>
<tr>
<td>3.9.0</td>
<td><s>September 22nd, 2026</s> September 15th, 2026</td>
<td>September 29th, 2026</td>
<td></td>
<td></td>
</tr>
</table>
</body></html>
'''

    @Test
    void testParsesAllScheduleRows() {
        def schedules = new ReleaseScheduleParser(SAMPLE_HTML).parseSchedule()
        assert schedules.size() == 4
        assert schedules.collect { it.version } == ['3.5.0', '2.19.6', '3.8.0', '3.9.0']
    }

    @Test
    void testParsesNormalRowFields() {
        def row = new ReleaseScheduleParser(SAMPLE_HTML).parseSchedule().find { it.version == '3.5.0' }
        assert row.rcDate == '2026-01-27'
        assert row.releaseDate == '2026-02-10'
        assert row.releaseManager == ['Foo']
        assert row.releaseIssue == 'https://github.com/opensearch-project/opensearch-build/issues/5897'
    }

    @Test
    void testRevisedReleaseDateUsesLiveValueNotStruckThrough() {
        // <strike>June 30th</strike> July 2nd -> July 2nd
        def row = new ReleaseScheduleParser(SAMPLE_HTML).parseSchedule().find { it.version == '2.19.6' }
        assert row.releaseDate == '2026-07-02'
    }

    @Test
    void testRevisedRcDateUsesLiveValueNotStruckThrough() {
        // <s>July 14th</s> July 21st -> July 21st
        def row = new ReleaseScheduleParser(SAMPLE_HTML).parseSchedule().find { it.version == '3.8.0' }
        assert row.rcDate == '2026-07-21'
    }

    @Test
    void testMultipleReleaseManagersParsedAsList() {
        def row = new ReleaseScheduleParser(SAMPLE_HTML).parseSchedule().find { it.version == '3.8.0' }
        assert row.releaseManager == ['Alice', 'Bob']
    }

    @Test
    void testRowWithEmptyManagerAndIssueStillParses() {
        def row = new ReleaseScheduleParser(SAMPLE_HTML).parseSchedule().find { it.version == '3.9.0' }
        assert row.rcDate == '2026-09-15'   // revised, live value
        assert row.releaseDate == '2026-09-29'
        assert row.releaseManager == []
        assert row.releaseIssue == null
    }

    @Test
    void testReturnsEmptyListWhenScheduleTableAbsent() {
        assert new ReleaseScheduleParser('<html><body>no table here</body></html>').parseSchedule() == []
    }

    @Test
    void testExtractManagersHandlesCommaSeparatedPlainText() {
        // No anchors, comma-separated names.
        assert ReleaseScheduleParser.extractManagers('Alice, Bob') == ['Alice', 'Bob']
        assert ReleaseScheduleParser.extractManagers('') == []
        assert ReleaseScheduleParser.extractManagers(null) == []
    }

    @Test
    void testNormalizeDateHandlesOrdinalsAndInvalid() {
        assert ReleaseScheduleParser.normalizeDate('January 27th, 2026') == '2026-01-27'
        assert ReleaseScheduleParser.normalizeDate('August 4th, 2026') == '2026-08-04'
        assert ReleaseScheduleParser.normalizeDate('not a date') == null
        assert ReleaseScheduleParser.normalizeDate(null) == null
    }
}
