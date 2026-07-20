/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses the OpenSearch release schedule table from the rendered opensearch.org/releases.html page.
 *
 * The page (a WordPress site, HTML only) contains a table with class
 * 'desktop-release-schedule-table' whose rows are:
 *   Release Number | First RC Generated | Latest Possible Release Date | Release Manager | Tracking Issue
 *
 * Each parsed row is normalized to a Map:
 *   [version, rcDate (yyyy-MM-dd), releaseDate (yyyy-MM-dd), releaseManager (List<String>), releaseIssue (URL)]
 *
 * A release can have more than one release manager, so releaseManager is always a list
 * (empty when the cell is blank).
 *
 * Revised dates: when a date is changed, the original is kept as struck-through text
 * (<strike>/<s>/<del>) followed by the new live date, e.g.
 *   "<strike>June 30th, 2026</strike> July 2nd, 2026".
 * The struck-through element and its contents are removed before parsing so only the
 * current (live) value is used.
 *
 * Rows that cannot be parsed (e.g. malformed dates) are skipped rather than failing the whole parse,
 * since this scrapes external HTML that can change.
 */
class ReleaseScheduleParser {

    static final String SCHEDULE_TABLE_CLASS = 'desktop-release-schedule-table'
    private static final String STRIKETHROUGH_PATTERN = /(?s)<(strike|s|del)[^>]*>.*?<\/\1>/

    String html

    ReleaseScheduleParser(String html) {
        this.html = html
    }

    /**
     * Returns the parsed schedule rows as a list of maps. Empty if the table is absent.
     */
    List<Map> parseSchedule() {
        String table = extractScheduleTable(html)
        if (!table) {
            return []
        }
        List<Map> schedules = []
        def rows = (table =~ /(?s)<tr>(.*?)<\/tr>/)
        rows.each { match ->
            String rowHtml = match[1]
            def cells = (rowHtml =~ /(?s)<td>(.*?)<\/td>/).collect { it[1] }
            // Skip the header row (uses <th>) and any malformed row.
            if (cells.size() < 5) {
                return
            }
            Map row = parseRow(cells)
            if (row) {
                schedules.add(row)
            }
        }
        return schedules
    }

    private Map parseRow(List cells) {
        String version = cleanText(cells[0])
        String rcDate = normalizeDate(cleanText(cells[1]))
        String releaseDate = normalizeDate(cleanText(cells[2]))
        List<String> releaseManagers = extractManagers(cells[3])
        String releaseIssue = extractAnchorUrl(cells[4])

        if (!version || !rcDate || !releaseDate) {
            return null
        }
        return [
            version       : version,
            rcDate        : rcDate,
            releaseDate   : releaseDate,
            releaseManager: releaseManagers,
            releaseIssue  : releaseIssue
        ]
    }

    private static String extractScheduleTable(String html) {
        def matcher = (html =~ /(?s)<table class="${SCHEDULE_TABLE_CLASS}".*?<\/table>/)
        return matcher ? matcher[0] : null
    }

    /**
     * Converts a display date like "January 27th, 2026" to ISO "2026-01-27".
     * Returns null if the value cannot be parsed.
     */
    static String normalizeDate(String display) {
        if (!display) {
            return null
        }
        // Strip ordinal suffixes (1st, 2nd, 3rd, 27th) and collapse whitespace.
        String cleaned = display.replaceAll(/(\d+)(st|nd|rd|th)/, '$1').replaceAll(/\s+/, ' ').trim()
        try {
            def formatter = DateTimeFormatter.ofPattern('MMMM d, yyyy', Locale.ENGLISH)
            return LocalDate.parse(cleaned, formatter).toString()
        } catch (Exception ignored) {
            return null
        }
    }

    /**
     * Extracts the release manager name(s) from a cell, ignoring struck-through (revised) content.
     * A cell may contain multiple managers as separate anchors, or as comma-separated plain text.
     * Returns an empty list when the cell is blank.
     */
    static List<String> extractManagers(String cell) {
        if (!cell) {
            return []
        }
        String live = cell.replaceAll(STRIKETHROUGH_PATTERN, '')
        // Prefer anchor texts when present (one manager per <a>).
        def anchors = (live =~ /(?s)<a[^>]*>(.*?)<\/a>/).collect { stripTags(it[1]).trim() }
        List<String> names
        if (anchors) {
            names = anchors
        } else {
            // No anchors: treat as comma-separated plain text.
            names = stripTags(live).split(',').collect { it.trim() }
        }
        return names.findAll { it }
    }

    // Returns the href URL of the (non-struck) anchor in the cell, or null.
    private static String extractAnchorUrl(String cell) {
        if (!cell) {
            return null
        }
        String live = cell.replaceAll(STRIKETHROUGH_PATTERN, '')
        def matcher = (live =~ /<a[^>]*href="([^"]*)"/)
        return matcher ? matcher[0][1] : null
    }

    /**
     * Removes struck-through (revised) content, strips remaining tags, and collapses whitespace.
     * Keeps only the current live value when a cell contains a revision.
     */
    private static String cleanText(String cell) {
        if (!cell) {
            return cell
        }
        String live = cell.replaceAll(STRIKETHROUGH_PATTERN, '')
        return stripTags(live).replaceAll(/\s+/, ' ').trim()
    }

    private static String stripTags(String value) {
        return value ? value.replaceAll(/<[^>]+>/, '').trim() : value
    }
}
