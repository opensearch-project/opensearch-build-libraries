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
 *   [version, rcDate (yyyy-MM-dd), releaseDate (yyyy-MM-dd), releaseManager (List<String>),
 *    releaseManagerGhHandle (List<String>), releaseIssue (URL)]
 *
 * A release can have more than one release manager, so releaseManager is always a list
 * (empty when the cell is blank). Managers are linked to their GitHub profiles, so
 * releaseManagerGhHandle carries their handles in the same order.
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
        // Use explicit Matcher.find() loops (not Matcher.each/.collect) to stay safe under the
        // Jenkins pipeline runtime, which does not handle iterating a Matcher well.
        // Tag patterns tolerate attributes and case (e.g. <tr class="...">, <TD>) so markup
        // changes on the scraped page don't silently drop rows/cells.
        def rowMatcher = (table =~ /(?si)<tr\b[^>]*>(.*?)<\/tr>/)
        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1)
            List<String> cells = extractCells(rowHtml)
            // Skip the header row (uses <th>) and any malformed row.
            if (cells.size() < 5) {
                continue
            }
            Map row = parseRow(cells)
            if (row) {
                schedules.add(row)
            }
        }
        return schedules
    }

    private static List<String> extractCells(String rowHtml) {
        List<String> cells = []
        def cellMatcher = (rowHtml =~ /(?si)<td\b[^>]*>(.*?)<\/td>/)
        while (cellMatcher.find()) {
            cells.add(cellMatcher.group(1))
        }
        return cells
    }

    private Map parseRow(List cells) {
        String version = cleanText(cells[0])
        String rcDate = normalizeDate(cleanText(cells[1]))
        String releaseDate = normalizeDate(cleanText(cells[2]))
        List<String> releaseManagers = extractManagers(cells[3])
        List<String> releaseManagerHandles = extractManagerHandles(cells[3])
        String releaseIssue = extractAnchorUrl(cells[4])

        if (!version || !rcDate || !releaseDate) {
            return null
        }
        return [
            version               : version,
            rcDate                : rcDate,
            releaseDate           : releaseDate,
            releaseManager        : releaseManagers,
            releaseManagerGhHandle: releaseManagerHandles,
            releaseIssue          : releaseIssue
        ]
    }

    private static String extractScheduleTable(String html) {
        // Matches the release schedule table on opensearch.org/releases.html by its CSS class.
        def matcher = (html =~ /(?s)<table class="desktop-release-schedule-table".*?<\/table>/)
        return matcher.find() ? matcher.group() : null
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
        List<String> names = []
        def anchorMatcher = (live =~ /(?s)<a[^>]*>(.*?)<\/a>/)
        while (anchorMatcher.find()) {
            names.add(stripTags(anchorMatcher.group(1)).trim())
        }
        if (names.isEmpty()) {
            // No anchors: treat as comma-separated plain text.
            names = stripTags(live).split(',').collect { it.trim() }
        }
        return names.findAll { it }
    }

    /**
     * Extracts the release managers' GitHub handles from a cell, in the same order as
     * extractManagers returns their names. Each manager is linked to their GitHub profile
     * ("<a href="https://github.com/gaiksaya">Sayali Gaikawad</a>"), and the handle is what the
     * identity table maps to a Slack user, so it is the only way a notification can tag them.
     *
     * Returns an empty list unless every manager in the cell yields a handle, so the handles are
     * either absent or positionally aligned with the names - a short list would leave callers
     * unable to tell which name a handle belongs to.
     */
    static List<String> extractManagerHandles(String cell) {
        if (!cell) {
            return []
        }
        String live = cell.replaceAll(STRIKETHROUGH_PATTERN, '')
        List<String> handles = []
        int anchors = 0
        def anchorMatcher = (live =~ /(?si)<a\b([^>]*)>(.*?)<\/a>/)
        while (anchorMatcher.find()) {
            anchors++
            String handle = handleFromProfileUrl(anchorMatcher.group(1))
            if (handle) {
                handles.add(handle)
            }
        }
        return (anchors > 0 && handles.size() == anchors) ? handles : []
    }

    /**
     * Reduces an anchor's attributes to the GitHub handle its href points at, or null when there is
     * no href or it is not a github.com user profile. The page links some managers with a trailing
     * slash ('github.com/gaiksaya/'), which would otherwise become part of the handle, and a
     * repository link (github.com/org/repo) is not a person at all.
     */
    private static String handleFromProfileUrl(String attributes) {
        def hrefMatcher = (attributes =~ /(?i)href\s*=\s*"([^"]*)"/)
        if (!hrefMatcher.find()) {
            return null
        }
        def profileMatcher = (hrefMatcher.group(1).trim() =~ /(?i)^https?:\/\/(?:www\.)?github\.com\/(.*)$/)
        if (!profileMatcher.find()) {
            return null
        }
        String path = profileMatcher.group(1)
        for (String separator : ['?', '#']) {
            int index = path.indexOf(separator)
            if (index >= 0) {
                path = path.substring(0, index)
            }
        }
        path = path.replaceAll(/\/+$/, '').trim()
        if (!path || path.contains('/')) {
            return null
        }
        return path.startsWith('@') ? path.substring(1) : path
    }

    // Returns the href URL of the (non-struck) anchor in the cell, or null.
    private static String extractAnchorUrl(String cell) {
        if (!cell) {
            return null
        }
        String live = cell.replaceAll(STRIKETHROUGH_PATTERN, '')
        def matcher = (live =~ /<a[^>]*href="([^"]*)"/)
        return matcher.find() ? matcher.group(1) : null
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
