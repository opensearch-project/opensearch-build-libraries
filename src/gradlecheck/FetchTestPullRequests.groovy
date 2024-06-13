/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package gradlecheck

import groovy.json.JsonOutput
import gradlecheck.OpenSearchMetricsQuery

class FetchTestPullRequests  {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    def script

    FetchTestPullRequests(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.script = script
    }

    def getQuery(testName) {
        def queryMap = [
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
        ]

        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }
    List<String> getTestPullRequests(testName) {
        def jsonResponse = new OpenSearchMetricsQuery(metricsUrl,awsAccessKey, awsSecretKey, awsSessionToken, script).fetchMetrics(getQuery(testName))
        def keys = jsonResponse.aggregations.pull_request_keyword_agg.buckets.collect { it.key }
        return keys
    }

}