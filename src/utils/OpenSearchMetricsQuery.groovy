/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils

import groovy.json.JsonSlurper

class OpenSearchMetricsQuery {
    String metricsUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    String indexName
    def script

    OpenSearchMetricsQuery(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, String indexName, def script) {
        this.metricsUrl = metricsUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.indexName = indexName
        this.script = script
    }

    def fetchMetrics(String query) {
        def response = script.sh(
            script: """
            set -e
            set +x
            curl -s -XGET "${metricsUrl}/${indexName}/_search" --aws-sigv4 "aws:amz:us-east-1:es" --user "${awsAccessKey}:${awsSecretKey}" -H "x-amz-security-token:${awsSessionToken}" -H 'Content-Type: application/json' -d "${query}" | jq '.'
        """,
        returnStdout: true
        ).trim()
        return new JsonSlurper().parseText(response)
    }
}
