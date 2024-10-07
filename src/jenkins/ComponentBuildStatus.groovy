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

class ComponentBuildStatus {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String indexName
    String product 
    String version
    String distributionBuildNumber
    String buildStartTimeFrom
    String buildStartTimeTo
    def script
    OpenSearchMetricsQuery openSearchMetricsQuery

    ComponentBuildStatus(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, String product, String version, String distributionBuildNumber, String buildStartTimeFrom, String buildStartTimeTo, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.product = product
        this.version = version
        this.distributionBuildNumber = distributionBuildNumber
        this.buildStartTimeFrom = buildStartTimeFrom
        this.buildStartTimeTo = buildStartTimeTo
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl,awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

     ComponentBuildStatus(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, String product, String version, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.product = product
        this.version = version
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl,awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

    def getQuery(String componentBuildResult) {
        def queryMap = [
                _source: [
                        "component",
                        ], 
                query: [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component_category: "${this.product}"
                                                        ]
                                                ],
                                        [
                                                match_phrase: [
                                                        component_build_result: "${componentBuildResult}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ],
                                        [
                                                match_phrase : [
                                                        distribution_build_number : "${this.distributionBuildNumber}"
                                                ]
                                        ],
                                        [
                                                range: [
                                                        build_start_time: [
                                                                from: "${this.buildStartTimeFrom}",
                                                                to: "${this.buildStartTimeTo}"
                                                                ]
                                                        ]
                                        ]
                                ]
                        ]
                ]
        ]
        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def getLatestDistributionBuildNumberQuery() {
        def queryMap = [
                size : 1,
                _source: [
                        "distribution_build_number",
                        ], 
                query: [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component_category: "${this.product}"
                                                        ]
                                                ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ]
                                ]
                        ]
                ],
                sort : [
                        [
                                build_start_time: [
                                        order: "desc"
                                ]
                        ]
                ]
        ]
        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def getComponents(String componentBuildResult) {
         def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getQuery(componentBuildResult))
         def components = jsonResponse.hits.hits.collect { it._source.component }
         return components
    }

    def getLatestDistributionBuildNumber() {
         def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getLatestDistributionBuildNumberQuery())
         def latestDistributionBuildNumber = jsonResponse.hits.hits[0]._source.distribution_build_number
         return latestDistributionBuildNumber
    }
}
