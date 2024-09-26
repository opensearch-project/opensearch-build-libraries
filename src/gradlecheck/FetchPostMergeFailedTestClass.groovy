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
import utils.OpenSearchMetricsQuery

class FetchPostMergeFailedTestClass {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String indexName
    def script

    FetchPostMergeFailedTestClass(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.script = script
    }

    def getQuery(timeFrame) {
        def queryMap = [
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
                                        ]
                                ],
                                filter: [
                                        [
                                                range: [
                                                        build_start_time: [
                                                                from: "now-${timeFrame}",
                                                                to: "now",
                                                                include_lower: true,
                                                                include_upper: true,
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
                        test_class_keyword_agg: [
                                terms: [
                                        field: "test_class",
                                        size: 500
                                ]
                        ]
                ]
        ]
        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def getPostMergeFailedTestClass(timeFrame) {
         def jsonResponse = new OpenSearchMetricsQuery(metricsUrl,awsAccessKey, awsSecretKey, awsSessionToken, indexName, script).fetchMetrics(getQuery(timeFrame))
         def keys = jsonResponse.aggregations.test_class_keyword_agg.buckets.collect { it.key }
         return keys
    }
}
