/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck

import org.junit.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class FetchPostMergeTestGitReferenceTest {

    private FetchPostMergeTestGitReference fetchPostMergeTestGitReference
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
                        "git_reference_keyword_agg": {
                            "buckets": [
                                {"key": "gitReference1"},
                                {"key": "gitReference2"}
                            ]
                        }
                    }
                }
                """
            }
            return ""
        }
        fetchPostMergeTestGitReference = new FetchPostMergeTestGitReference(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
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
                                        ]
                                ],
                                adjust_pure_negative: true,
                                boost: 1
                        ]
                ],
                aggregations: [
                        git_reference_keyword_agg: [
                                terms: [
                                        field: "git_reference.keyword",
                                        size: 500
                                ]
                        ]
                ]
        ]).replace('"', '\\"')

        def result = fetchPostMergeTestGitReference.getQuery(testName)

        assert result == expectedOutput
    }

    @Test
    void testGetPostMergeTestGitReferenceReturnsKeys() {
        def testName = "ExampleTest"
        def expectedOutput = ["gitReference1", "gitReference2"]

        def result = fetchPostMergeTestGitReference.getPostMergeTestGitReference(testName)

        assert result == expectedOutput
    }
}
