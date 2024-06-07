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
import groovy.mock.interceptor.MockFor

class FetchPostMergeFailedTestClassTest {

    private FetchPostMergeFailedTestClass fetchPostMergeFailedTestClass
    private final String metricsUrl = "http://example.com"
    private final String awsAccessKey = "testAccessKey"
    private final String awsSecretKey = "testSecretKey"
    private final String awsSessionToken = "testSessionToken"
    private def script

    @Before
    void setUp() {
        script = new Expando()
        script.sh = { Map args ->
            if (args.containsKey("script")) {
                return """
                {
                    "aggregations": {
                        "test_class_keyword_agg": {
                            "buckets": [
                                {"key": "testClass1"},
                                {"key": "testClass2"}
                            ]
                        }
                    }
                }
                """
            }
            return ""
        }
        fetchPostMergeFailedTestClass = new FetchPostMergeFailedTestClass(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, script)
    }

    @Test
    void testGetQueryReturnsExpectedQuery() {
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
                                                                operator: "OR"
                                                        ]
                                                ]
                                        ]
                                ]
                        ]
                ],
                aggregations: [
                        test_class_keyword_agg: [
                                terms: [
                                        field: "test_class",
                                        size: 2
                                ]
                        ]
                ]
        ]).replace('"', '\\"')

        def result = fetchPostMergeFailedTestClass.getQuery()

        assert result == expectedOutput
    }

    @Test
    void testGetPostMergeFailedTestClassReturnsKeys() {
        def expectedOutput = ["testClass1", "testClass2"]

        def result = fetchPostMergeFailedTestClass.getPostMergeFailedTestClass()

        assert result == expectedOutput
    }
}
