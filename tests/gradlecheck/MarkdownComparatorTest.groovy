/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MarkdownComparatorTest {

    @Test
    public void testMarkdownComparisonWithDifferences() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        testReportMarkdown.add(createRow("def456", "PR2", "Build2", "Test2"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(1, differences.size());
        Assert.assertEquals("def456", differences.get(0).get("Git Reference"));
        Assert.assertEquals("PR2", differences.get(0).get("Merged Pull Request"));
        Assert.assertEquals("Build2", differences.get(0).get("Build Details"));
        Assert.assertEquals("Test2", differences.get(0).get("Test Name"));
    }

    @Test
    public void testMarkdownComparisonWithoutDifferences() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    private Map<String, String> createRow(String gitReference, String mergedPR, String buildDetails, String testName) {
        Map<String, String> row = new HashMap<>();
        row.put("Git Reference", gitReference);
        row.put("Merged Pull Request", mergedPR);
        row.put("Build Details", buildDetails);
        row.put("Test Name", testName);
        return row;
    }

    @Test
    public void testMarkdownComparisonWithPartialMatch() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        testReportMarkdown.add(createRow("def456", "PR2", "Build2", "Test2"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        gitHubMarkdown.add(createRow("ghi789", "PR3", "Build3", "Test3"));
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(1, differences.size());
        Assert.assertEquals("def456", differences.get(0).get("Git Reference"));
        Assert.assertEquals("PR2", differences.get(0).get("Merged Pull Request"));
        Assert.assertEquals("Build2", differences.get(0).get("Build Details"));
        Assert.assertEquals("Test2", differences.get(0).get("Test Name"));
    }

    @Test
    public void testMarkdownComparisonWithEmptyList() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    @Test
    public void testMarkdownComparisonWithDifferentOrder() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        testReportMarkdown.add(createRow("def456", "PR2", "Build2", "Test2"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("def456", "PR2", "Build2", "Test2"));
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    @Test
    public void testMarkdownComparisonWithIdenticalEntries() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    @Test
    public void testMarkdownComparisonWithNullValues() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow(null, "PR1", "Build1", "Test1"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow(null, "PR1", "Build1", "Test1"));
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    @Test
    public void testMarkdownComparisonWithDifferentColumns() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        Map<String, String> gitHubRow = createRow("abc123", "PR1", "Build1", "Test1");
        gitHubRow.put("Extra Column", "ExtraValue");
        gitHubMarkdown.add(gitHubRow);
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    @Test
    public void testMarkdownComparisonCompleteMatch() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));

        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    @Test
    public void testMarkdownComparisonPartialMatch() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test2")); // Different 'Test Name'

        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(1, differences.size());
        Assert.assertEquals("abc123", differences.get(0).get("Git Reference"));
    }

    @Test
    public void testMarkdownComparisonNoMatch() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("def456", "PR2", "Build2", "Test2"));

        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(1, differences.size());
        Assert.assertEquals("abc123", differences.get(0).get("Git Reference"));
    }

    @Test
    public void testMarkdownComparisonWithEmptyLists() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();

        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }

    @Test
    public void testMarkdownComparisonMultipleMatches() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1")); // Duplicate entry
        testReportMarkdown.add(createRow("def456", "PR2", "Build2", "Test2"));
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1")); // Matching row
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1")); // Another matching row
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(1, differences.size());
        Assert.assertEquals("def456", differences.get(0).get("Git Reference")); // Ensure correct difference
    }

    @Test
    public void testMarkdownComparisonDifferentDataTypes() {
        List<Map<String, String>> testReportMarkdown = new ArrayList<>();
        testReportMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        testReportMarkdown.add(createRow(null, "PR2", "Build2", "Test2")); // Null value for Git Reference
        List<Map<String, String>> gitHubMarkdown = new ArrayList<>();
        gitHubMarkdown.add(createRow("abc123", "PR1", "Build1", "Test1"));
        gitHubMarkdown.add(createRow(null, "PR2", "Build2", "Test2"));
        MarkdownComparator comparator = new MarkdownComparator(testReportMarkdown, gitHubMarkdown);
        List<Map<String, String>> differences = comparator.markdownComparison();
        Assert.assertEquals(0, differences.size());
    }
}
