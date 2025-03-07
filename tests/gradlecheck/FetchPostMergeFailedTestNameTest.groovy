/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck.tests

import gradlecheck.FetchPostMergeFailedTestName
import org.junit.Before
import org.junit.Test
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class FetchPostMergeFailedTestNameTest {

    private FetchPostMergeFailedTestName fetchPostMergeFailedTestName
    private final String metricsUrl = "http://example.com"
    private final String awsAccessKey = "testAccessKey"
    private final String awsSecretKey = "testSecretKey"
    private final String awsSessionToken = "testSessionToken"
    private final String indexName = "gradle-check-*"
    private def script

    @Before
    void setUp() {
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return """
                {
                    "aggregations": {
                        "test_name_keyword_agg": {
                            "buckets": [
                                {"key": "testName1"},
                                {"key": "testName2"}
                            ]
                        },
                        "build_number_agg": {
                            "buckets": [
                                {"key": "buildNumber1"},
                                {"key": "buildNumber2"}
                            ]
                        },
                        "pull_request_agg": {
                            "buckets": [
                                {"key": "pullRequest1"},
                                {"key": "pullRequest2"}
                            ]
                        }
                    }
                }
                """
            }
            return ""
        }
        fetchPostMergeFailedTestName = new FetchPostMergeFailedTestName(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

    @Test
    void testGetQueryReturnsExpectedQuery() {
        def testName = "ExampleTest"
        def gitReference = "abc123"
        def expectedOutput = JsonOutput.toJson([
                size: 200,
                query: [
                        bool: [
                                must: [
                                        [
                                                match: [
                                                        "invoke_type.keyword": [
                                                                query: "Post Merge Action",
                                                                operator: "OR",
                                                                prefix_length: 0,
                                                                max_expansions: 50,
                                                                fuzzy_transpositions: true,
                                                                lenient: false,
                                                                zero_terms_query: "NONE",
                                                                auto_generate_synonyms_phrase_query: true,
                                                                boost: 1
                                                        ]
                                                ]
                                        ],
                                        [
                                                match: [
                                                        test_status: [
                                                                query: "FAILED",
                                                                operator: "OR",
                                                                prefix_length: 0,
                                                                max_expansions: 50,
                                                                fuzzy_transpositions: true,
                                                                lenient: false,
                                                                zero_terms_query: "NONE",
                                                                auto_generate_synonyms_phrase_query: true,
                                                                boost: 1
                                                        ]
                                                ]
                                        ],
                                        [
                                                match: [
                                                        test_class: [
                                                                query: testName,
                                                                operator: "OR",
                                                                prefix_length: 0,
                                                                max_expansions: 50,
                                                                fuzzy_transpositions: true,
                                                                lenient: false,
                                                                zero_terms_query: "NONE",
                                                                auto_generate_synonyms_phrase_query: true,
                                                                boost: 1
                                                        ]
                                                ]
                                        ],
                                        [
                                                match: [
                                                        "git_reference.keyword": [
                                                                query: gitReference,
                                                                operator: "OR",
                                                                prefix_length: 0,
                                                                max_expansions: 50,
                                                                fuzzy_transpositions: true,
                                                                lenient: false,
                                                                zero_terms_query: "NONE",
                                                                auto_generate_synonyms_phrase_query: true,
                                                                boost: 1
                                                        ]
                                                ]
                                        ]
                                ],
                                adjust_pure_negative: true,
                                boost: 1
                        ]
                ],
                aggregations: [
                        test_name_keyword_agg: [
                                terms: [
                                        field: "test_name",
                                        size: 500
                                ]
                        ],
                        build_number_agg: [
                                terms: [
                                        field: "build_number",
                                        size: 500
                                ]
                        ],
                        pull_request_agg: [
                                terms: [
                                        field: "pull_request",
                                        size: 500
                                ]
                        ]
                ]
        ]).replace('"', '\\"')

        def result = fetchPostMergeFailedTestName.getQuery(testName, gitReference)

        assert result == expectedOutput
    }

    @Test
    void testGetPostMergeFailedTestNameReturnsMetrics() {
        def testName = "ExampleTest"
        def gitReference = "abc123"
        def expectedOutput = new JsonSlurper().parseText("""
        {
            "aggregations": {
                "test_name_keyword_agg": {
                    "buckets": [
                        {"key": "testName1"},
                        {"key": "testName2"}
                    ]
                },
                "build_number_agg": {
                    "buckets": [
                        {"key": "buildNumber1"},
                        {"key": "buildNumber2"}
                    ]
                },
                "pull_request_agg": {
                    "buckets": [
                        {"key": "pullRequest1"},
                        {"key": "pullRequest2"}
                    ]
                }
            }
        }
        """)

        def result = fetchPostMergeFailedTestName.getPostMergeFailedTestName(testName, gitReference)

        assert result == expectedOutput
    }
}
