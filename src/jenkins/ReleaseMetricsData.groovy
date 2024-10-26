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

class ReleaseMetricsData {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String indexName
    String version
    def script
    OpenSearchMetricsQuery openSearchMetricsQuery

    ReleaseMetricsData(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, String version, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.version = version
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

    String getReleaseOwnerQuery(String component) {
        def queryMap = [
                size   : 1,
                _source: "release_owners",
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
                                                        "component": "${component}"
                                                ]
                                        ]
                                ]
                        ]
                ],
                sort : [
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

    ArrayList getReleaseOwners(String component) {
        def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getReleaseOwnerQuery(component))
        def releaseOwners = jsonResponse.hits.hits._source.release_owners.flatten()
        return releaseOwners
    }
}
