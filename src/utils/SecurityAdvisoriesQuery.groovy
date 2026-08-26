/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils

import groovy.json.JsonSlurperClassic

/**
 * SigV4-signed read client for the security advisories OpenSearch cluster.
 *
 * This is a separate cluster from the metrics cluster (different endpoint and credentials), so it
 * has its own client rather than reusing OpenSearchMetricsQuery. It intentionally exposes reads
 * only: the release-readiness checks consume advisory/scan data but never write to this cluster.
 *
 * The target index is passed per query rather than bound to the instance, because callers span
 * several indices (a cluster-wide search to resolve the latest scans index, then that scans index,
 * then the advisories index) with a single client.
 */
class SecurityAdvisoriesQuery {
    private static final String SIGV4_REGION = 'us-east-1'

    String advisoriesUrl
    String awsAccessKey
    String awsSecretKey
    String awsSessionToken
    def script

    SecurityAdvisoriesQuery(String advisoriesUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, def script) {
        this.advisoriesUrl = advisoriesUrl
        this.awsAccessKey = awsAccessKey
        this.awsSecretKey = awsSecretKey
        this.awsSessionToken = awsSessionToken
        this.script = script
    }

    /**
     * Runs a search against an explicit index.
     * @param targetIndex the index to search
     * @param query the SigV4-shell-escaped query body
     */
    def search(String targetIndex, String query) {
        this.script.println("Querying advisories index '${targetIndex}' with query: ${query}")
        def response = runSearch("${advisoriesUrl}/${targetIndex}/_search", query)
        this.script.println("Advisories index '${targetIndex}' returned ${response?.hits?.hits?.size() ?: 0} hit(s).")
        return response
    }

    /**
     * Runs an unscoped search (GET /_search with no index in the path) to resolve the most recent
     * scan index. The path deliberately carries no index name: a wildcard like scans-* would be
     * percent-encoded by the SigV4 signer and then read as a literal index name (matching nothing),
     * so the newest scan index is found by searching across the cluster and sorting by _index.
     * @param query the SigV4-shell-escaped query body
     */
    def searchForLatestScanIndex(String query) {
        this.script.println("Resolving the latest scan index with query: ${query}")
        def response = runSearch("${advisoriesUrl}/_search", query)
        this.script.println("Latest-scan-index resolution matched ${response?.hits?.hits?.size() ?: 0} scan doc(s).")
        return response
    }

    // The cluster endpoint is a secret, so the URL itself is never logged (callers log the index
    // and query instead). Issues the signed request, then fails loudly on an empty body or a cluster
    // error response so a failed query throws (and the caller records the criterion as unknown)
    // instead of parsing to an error object that silently looks like zero hits. An empty hits list
    // is a valid, non-error response and is returned as-is.
    private def runSearch(String url, String query) {
        def rawResponse = script.sh(
            script: """
            set -e
            set +x
            curl -s -XGET "${url}" ${curlAuthArgs()} -H 'Content-Type: application/json' -d "${query}" | jq '.'
        """,
        returnStdout: true
        ).trim()
        if (!rawResponse) {
            script.error('Advisories cluster returned an empty response.')
        }
        def response = new JsonSlurperClassic().parseText(rawResponse)
        if (response?.error) {
            script.error("Advisories cluster query failed: ${response.error}")
        }
        return response
    }

    /**
     * Common SigV4 authentication arguments shared by every cluster request.
     * Keeping this private ensures credentials and the signing region are defined once.
     */
    private String curlAuthArgs() {
        return "--aws-sigv4 \"aws:amz:${SIGV4_REGION}:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\""
    }
}
