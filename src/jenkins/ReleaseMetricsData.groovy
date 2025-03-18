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
    public static final String INDEX_NAME = 'opensearch_release_metrics'
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String version
    String indexName
    def script
    def openSearchMetricsQuery

    ReleaseMetricsData(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String version, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.version = version
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, INDEX_NAME, script)
    }

    ReleaseMetricsData(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String version, String indexName, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.version = version
        this.indexName = indexName
        this.script = script
        this.openSearchMetricsQuery = new OpenSearchMetricsQuery(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, indexName, script)
    }

    String getReleaseOwnerIssueRepoQuery(String component) {
        def queryMap = [
                size   : 1,
                _source: [
                        "release_owners",
                        "release_issue_exists"
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
                                                        "component.keyword": "${component}"
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

    String getReleaseIssueQuery(String repository, String changedMatchPhraseKey = "repository") {
        def queryMap = [
                size   : 1,
                _source: "release_issue",
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
                                                        "${changedMatchPhraseKey}": "${repository}"
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

ArrayList getReleaseOwners(String component) {
        try {
                def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getReleaseOwnerIssueRepoQuery(component))
                def releaseOwners = jsonResponse.hits.hits._source.release_owners.flatten()
                return releaseOwners
        } catch (Exception e) {
                this.script.println("Error fetching release owners: ${e.message}")
                return null
        }
}

String getReleaseIssue(String repository, String changedMatchPhraseKey="repository") {
        try {
                def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getReleaseIssueQuery(repository, changedMatchPhraseKey))
                def releaseIssue = jsonResponse.hits.hits._source.release_issue[0]
                return releaseIssue.toString()
        } catch (Exception e) {
                this.script.println("Error fetching release issue: ${e.message}")
                return null
        }
}

def getReleaseIssueStatus(String component) {
        try {
                def jsonResponse = this.openSearchMetricsQuery.fetchMetrics(getReleaseOwnerIssueRepoQuery(component))
                def releaseIssueExists = jsonResponse.hits.hits[0]._source.release_issue_exists
                return releaseIssueExists
        } catch (Exception e) {
                this.script.println("Error fetching release issue status: ${e.message}")
                return null
        }
}
}
