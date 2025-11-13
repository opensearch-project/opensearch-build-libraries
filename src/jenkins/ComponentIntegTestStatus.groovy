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
import groovy.json.JsonSlurperClassic
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

    def componentIntegTestTopResultsQuery(String component) {
                def queryMap = [
                        size: 0,
                        query: [
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
                        ],
                        aggs: [
                                unique_combinations: [
                                        composite: [
                                                size: 100,
                                                sources: [
                                                        [
                                                                platform: [
                                                                        terms: [
                                                                                field: "platform"
                                                                        ]
                                                                ]
                                                        ],
                                                        [
                                                                architecture: [
                                                                        terms: [
                                                                                field: "architecture"
                                                                        ]
                                                                ]
                                                        ],
                                                        [
                                                                distribution: [
                                                                        terms: [
                                                                                field: "distribution"
                                                                        ]
                                                                ]
                                                        ]
                                                ]
                                        ],
                                        aggs: [
                                                latest_doc: [
                                                        top_hits: [
                                                                size: 1,
                                                                sort: [
                                                                        [
                                                                                integ_test_build_number: [
                                                                                        order: "desc"
                                                                                ]
                                                                        ]
                                                                ],
                                                                _source: [
                                                                        "platform",
                                                                        "architecture", 
                                                                        "distribution",
                                                                        "test_report_manifest_yml",
                                                                        "integ_test_build_url",
                                                                        "rc_number",
                                                                        "component_build_result"
                                                                ]
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
        try {
                def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(componentIntegTestTopResultsQuery(component))

                if (jsonResponse.hits.total.value == 0) {
                        this.script.println("No integration test failed data found for component: ${component}")
                        return null
                }
                else {
                        def failedBuilds = []
                        jsonResponse.aggregations.unique_combinations.buckets.each { bucket ->
                                def hit = bucket.latest_doc.hits.hits[0]
                                if (hit._source.component_build_result == "failed") {
                                        failedBuilds.add(hit)
                                }
                        }
                        return new JsonSlurperClassic().parseText(JsonOutput.toJson(failedBuilds))
                }
        } catch (Exception e) {
                this.script.println("Error getting component integration test failed data for ${component}: ${e.message}")
                return null
        }
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
