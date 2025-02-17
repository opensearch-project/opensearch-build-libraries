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

class ReleaseCandidateStatus {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String indexName
    String version
    def script
    def openSearchMetricsQuery

    ReleaseCandidateStatus(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, String version, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.version = version
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl,awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

    def getRcDistributionNumberQuery(Integer rcNumber = null, String componentName) {
        def queryMap = [
                _source: "distribution_build_number",
                sort: [
                    [
                        distribution_build_number: [
                            order: "desc"
                        ],
                        rc_number: [
                            order: "desc"
                        ]
                    ]
                ],
                size: 1,
                query: [
                    bool: [
                        filter: [
                            [
                                match_phrase: [
                                    component: "${componentName}"
                                ]
                            ],
                            [
                                match_phrase: [
                                    rc: "true"
                                ]
                            ],
                            [
                                match_phrase: [
                                    version: "${this.version}"
                                ]
                            ]
                        ]
                    ]
                ]
            ]

        if (rcNumber != null) {
            queryMap = [
                _source: "distribution_build_number",
                sort: [
                    [
                        distribution_build_number: [
                            order: "desc"
                        ]
                    ]
                ],
                size: 1,
                query: [
                    bool: [
                        filter: [
                            [
                                match_phrase: [
                                    component: "${componentName}"
                                ]
                            ],
                            [
                                match_phrase: [
                                    rc: "true"
                                ]
                            ],
                            [
                                match_phrase: [
                                    version: "${this.version}"
                                ]
                            ],
                            [
                                match_phrase: [
                                    rc_number: "${rcNumber}"
                                ]
                            ]
                        ]
                    ]
                ]
            ]
        }

        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def getLatestRcNumberQuery(String componentName){
        def queryMap = [
                _source: "rc_number",
                sort: [
                        [
                                distribution_build_number: [
                                        order: "desc"
                                ],
                                rc_number: [
                                        order: "desc"
                                ]
                        ]
                ],
                size: 1,
                query: [
                        bool: [
                                filter: [
                                        [
                                                match_phrase: [
                                                        component: "${componentName}"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        rc: "true"
                                                ]
                                        ],
                                        [
                                                match_phrase: [
                                                        version: "${this.version}"
                                                ]
                                        ]
                                ]
                        ]
                ]
        ]
        def query = JsonOutput.toJson(queryMap)
        return query.replace('"', '\\"')
    }

    def getRcDistributionNumber(Integer rcNumber = null, String componentName) {
         def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getRcDistributionNumberQuery(rcNumber, componentName))
         def rcDistributionNumber = jsonResponse.hits.hits[0]._source.distribution_build_number
         return rcDistributionNumber
    }

    def getLatestRcNumber(String componentName) {
        def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getLatestRcNumberQuery(componentName))
        def rcNumber = jsonResponse.hits.hits[0]._source.rc_number
        return rcNumber
    }

}
