/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import groovy.json.JsonOutput
import utils.OpenSearchMetricsQuery

class ComponentRepoData {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String version
    def script

    ComponentRepoData(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String version, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.version = version
        this.script = script
    }

    String getMaintainersQuery(String repository, Boolean inactive=false) {
        def queryMap = [
                size   : 100,
                _source: "github_login",
                collapse: [
                        field: "github_login.keyword"
                ],
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        "repository.keyword": "${repository}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        "inactive": "${inactive}"
                                                ]
                                        ]
                                ]
                        ]
                ],
                sort   : [
                        [
                                current_date: [
                                        order: "desc"
                                ]
                        ]
                ]
        ]
        String query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    String getCodeCoverageQuery(String repository) {
        def queryMap = [
                size   : 1,
                _source: [
                        "coverage",
                        "branch",
                        "state",
                        "url"
                ],
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        "repository.keyword": "${repository}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        "version": "${this.version}"
                                                ]
                                        ]
                                ]
                        ]
                ],
                sort   : [
                        [
                                current_date: [
                                        order: "desc"
                                ]
                        ]
                ]
        ]
        String query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    ArrayList getMaintainers(String repository, String indexName, Boolean inactive=false) {
        try {
            def openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
            def jsonResponse = openSearchMetricsQuery.fetchMetrics(getMaintainersQuery(repository, inactive))
            def maintainers = jsonResponse.hits.hits.collect { it._source.github_login }
            return maintainers
        } catch (Exception e) {
            this.script.println("Error fetching the maintainers for ${repository}: ${e.message}")
            return null
        }
    }

    def getCodeCoverage(String repository, String indexName) {
        try {
            def openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
            def jsonResponse = openSearchMetricsQuery.fetchMetrics(getCodeCoverageQuery(repository))
            return [
                    coverage: jsonResponse.hits.hits[0]._source.coverage,
                    state: jsonResponse.hits.hits[0]._source.state,
                    branch: jsonResponse.hits.hits[0]._source.branch,
                    url: jsonResponse.hits.hits[0]._source.url
            ]
        } catch (Exception e) {
            this.script.println("Error fetching code coverage metrics for ${repository}: ${e.message}")
            return null
        }
    }
}
