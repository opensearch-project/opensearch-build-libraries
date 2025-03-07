/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck.tests

import gradlecheck.FetchTestPullRequests
import org.junit.Before
import org.junit.Test
import groovy.json.JsonOutput

class FetchTestPullRequestsTest {

    private FetchTestPullRequests fetchTestPullRequests
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
                        "pull_request_keyword_agg": {
                            "buckets": [
                                {"key": "PR-1"},
                                {"key": "PR-2"}
                            ]
                        }
                    }
                }
                """
            }
            return ""
        }
        fetchTestPullRequests = new FetchTestPullRequests(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

    @Test
    void testGetQueryReturnsExpectedQuery() {
        def testName = "ExampleTest"
        def expectedOutput = JsonOutput.toJson([
            size: 200,
            query: [
                bool: [
                    must: [
                        [
                            match: [
                                "invoke_type.keyword": [
                                    query: "Pull Request",
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
                        ]
                    ],
                    adjust_pure_negative: true,
                    boost: 1
                ]
            ],
            aggregations: [
                pull_request_keyword_agg: [
                    terms: [
                        field: "pull_request",
                        size: 500
                    ]
                ]
            ]
        ]).replace('"', '\\"')

        def result = fetchTestPullRequests.getQuery(testName)

        assert result == expectedOutput
    }

    @Test
    void testGetTestPullRequestsReturnsKeys() {
        def testName = "ExampleTest"
        def expectedOutput = ["PR-1", "PR-2"]

        def result = fetchTestPullRequests.getTestPullRequests(testName)

        assert result == expectedOutput
    }
}
