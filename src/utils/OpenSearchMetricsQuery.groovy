/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package utils

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

class OpenSearchMetricsQuery {
    private static final String SIGV4_REGION = 'us-east-1'

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

    /**
     * Constructor for callers that operate across indices (e.g. index creation) and do not
     * need a fixed default index. Methods that take an explicit target index (indexExists,
     * createIndex) work with this; fetchMetrics requires the indexName-bound constructor.
     */
    OpenSearchMetricsQuery(String metricsUrl, String awsAccessKey, String awsSecretKey, String awsSessionToken, def script) {
        this(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, null, script)
    }

    def fetchMetrics(String query) {
        this.script.println('Running query: '+ query)
        def response = script.sh(
            script: """
            set -e
            set +x
            curl -s -XGET "${metricsUrl}/${indexName}/_search" ${curlAuthArgs()} -H 'Content-Type: application/json' -d "${query}" | jq '.'
        """,
        returnStdout: true
        ).trim()
        return new JsonSlurperClassic().parseText(response)
    }

    /**
     * Returns true if the given index already exists on the cluster.
     */
    boolean indexExists(String targetIndex) {
        String httpCode = script.sh(
            script: """
                set +x
                curl -s -o /dev/null -w '%{http_code}' -I "${metricsUrl}/${targetIndex}" ${curlAuthArgs()}
            """,
            returnStdout: true
        ).trim()
        return httpCode == '200'
    }

    /**
     * Creates an index with the given mapping. Throws if the cluster does not return 200 or 201.
     * @param targetIndex the index name to create
     * @param mapping a Map representing the index body (e.g. [mappings: [properties: [...]]])
     */
    void createIndex(String targetIndex, Map mapping) {
        String httpCode = sendJson('PUT', "${metricsUrl}/${targetIndex}", mapping)
        if (httpCode != '200' && httpCode != '201') {
            throw new RuntimeException("Failed to create index ${targetIndex}. HTTP status: ${httpCode}")
        }
    }

    /**
     * Indexes a single document into the given index (append; a new document is created each call).
     * Throws if the cluster does not return 200 or 201.
     * @param targetIndex the index to write to
     * @param document a Map representing the document body
     */
    void indexDocument(String targetIndex, Map document) {
        String httpCode = sendJson('POST', "${metricsUrl}/${targetIndex}/_doc", document)
        if (httpCode != '200' && httpCode != '201') {
            throw new RuntimeException("Failed to index document into ${targetIndex}. HTTP status: ${httpCode}")
        }
    }

    /**
     * Sends a JSON body to the cluster and returns the HTTP status code.
     *
     * The body is written to a temp file and passed via `curl -d @file` rather than inlined into
     * the shell command, so free-text fields containing quotes or shell metacharacters cannot break
     * the command or inject shell (the body never touches the shell parser).
     *
     * @param method HTTP method (PUT, POST, ...)
     * @param url full request URL
     * @param body a Map serialized to JSON as the request body
     */
    private String sendJson(String method, String url, Map body) {
        String bodyFile = "os-metrics-request-${UUID.randomUUID().toString()}.json"
        script.writeFile(file: bodyFile, text: JsonOutput.toJson(body))
        try {
            return script.sh(
                script: """
                    set +x
                    curl -s -o /dev/null -w '%{http_code}' -X${method} "${url}" ${curlAuthArgs()} -H 'Content-Type: application/json' -d @${bodyFile}
                """,
                returnStdout: true
            ).trim()
        } finally {
            script.sh(script: "rm -f ${bodyFile}", returnStatus: true)
        }
    }

    /**
     * Common SigV4 authentication arguments shared by every cluster request.
     * Keeping this private ensures credentials and the signing region are defined once.
     */
    private String curlAuthArgs() {
        return "--aws-sigv4 \"aws:amz:${SIGV4_REGION}:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\""
    }
}
