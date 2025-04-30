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

class ComponentIntegTestStatus {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String indexName
    String product
    String version
    String qualifier
    String distributionBuildNumber
    def script
    def openSearchMetricsQuery

    ComponentIntegTestStatus(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, String product, String version, String qualifier, String distributionBuildNumber, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.product = product
        this.version = version
        this.qualifier = qualifier
        this.distributionBuildNumber = distributionBuildNumber
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

    ComponentIntegTestStatus(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, String version, String qualifier, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.version = version
        this.qualifier = qualifier
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this.indexName, script)
    }

    def getQuery(String componentIntegTestResult) {
        def queryMap = [
                size   : 50,
                _source: [
                        "component"
                ],
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        component_category: "${this.product}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        distribution_build_number: "${this.distributionBuildNumber}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        component_build_result: "${componentIntegTestResult}"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]
        if (!isNullOrEmpty(this.qualifier)) {
            queryMap.query.bool.filter.add([
                    match_phrase: [
                            qualifier: "${this.qualifier}"
                    ]
            ])
        }
        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def componentIntegTestFailedDataQuery(String component) {
        def queryMap = [
                _source: [
                        "platform",
                        "architecture",
                        "distribution",
                        "test_report_manifest_yml",
                        "integ_test_build_url",
                        "rc_number"
                ],
                query  : [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component: "${component}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        distribution_build_number: "${this.distributionBuildNumber}"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]
        if (!isNullOrEmpty(this.qualifier)) {
            queryMap.query.bool.filter.add([
                    match_phrase: [
                            qualifier: "${this.qualifier}"
                    ]
            ])
        }
        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def termsQueryForComponents(Integer rcNumber, String distribution, String architecture, def components) {
        def queryMap = [
                size: 100,
                sort: [
                        [
                                build_start_time: [
                                    order: "desc"
                                ]
                        ]
                ],
                _source: [
                        "component",
                        "component_build_result"
                ],
                query  : [
                        bool: [
                                must: [
                                        [
                                                match_phrase: [
                                                        rc_number: "${rcNumber}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        distribution: "${distribution}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        architecture: "${architecture}"
                                                ]
                                        ],
                                        [
                                                terms: [
                                                        component: components
                                                ]
                                        ]
                                ]
                        ]
                ],
                collapse: [
                        field: "component"
                ]
        ]


        if (!isNullOrEmpty(this.qualifier)) {
            queryMap.query.bool.must.add([
                    match_phrase: [
                            qualifier: "${this.qualifier}"
                    ]
            ])
        }

        if (components.contains('OpenSearch-Dashboards')) {
            queryMap.query.bool.must.removeAll { it.containsKey('terms') }
            queryMap.query.bool.must.add([
                    bool: [
                            should: [
                                    [
                                            regexp: [
                                                    component: "OpenSearch-Dashboards-ci-group-.*"
                                            ]
                                    ],
                                    [
                                            terms: [
                                                    component: components
                                            ]
                                    ]
                            ]
                    ]
            ])
        }
        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def getComponents(String componentBuildResult) {
        def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getQuery(componentBuildResult))
        def components = jsonResponse.hits.hits.collect { it._source.component }
        return components
    }

    def getComponentIntegTestFailedData(String component) {
        def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(componentIntegTestFailedDataQuery(component))
        return jsonResponse
    }

    def getAllFailedComponents(Integer rcNumber, String distribution, String architecture, def components) {
        def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(
                termsQueryForComponents(rcNumber, distribution, architecture, components)
        )
        def failedComponents = jsonResponse.hits.hits.findAll { it ->
            it._source.component_build_result == 'failed'
        }.collect { it ->
            it._source.component
        }
        return failedComponents
    }


    private boolean isNullOrEmpty(String str) {
        return (str == 'Null' || str == null || str.allWhitespace || str.isEmpty()) || str == "None"
    }

}
