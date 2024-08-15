/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to fetch opensearch-benchmark test-execution-ids for baseline and contender (pull_request) benchmark runs.
 @param Map args = [:] args A map of the following parameters
 @param args.baselineClusterConfig <required> - The cluster-config user-tag value attached to metrics published from nightly baseline runs.
 @param args.distributionVersion <required> - The OpenSearch distribution version.
 @param args.workload <required> - The workload used for running benchmark.
 @param args.pullRequestNumber <required> - The pull_request_number user-tag value attached to metrics published from OS pull request runs
 */

import groovy.json.JsonSlurper

Map<String, String> call(Map args = [:]) {
    if (isNullOrEmpty(args.baselineClusterConfig.toString()) || isNullOrEmpty(args.distributionVersion.toString()) || isNullOrEmpty(args.workload.toString()) ||
    isNullOrEmpty(args.pullRequestNumber.toString())) {
        echo 'Please provide all the required parameters'
        return null
    }
    String baselineId = getBaselineTestExecutionId(args.baselineClusterConfig, args.distributionVersion, args.workload)
    String contenderId = getContenderTestExecutionId(args.pullRequestNumber)
    return ['baseline': baselineId, 'contender': contenderId]
}

String getBaselineTestExecutionId(baselineClusterConfig, distributionVersion, workload) {
    withCredentials([string(credentialsId: 'benchmark-metrics-datastore-user', variable: 'DATASTORE_USER'),
                     string(credentialsId: 'benchmark-metrics-datastore-password', variable: 'DATASTORE_PASSWORD'),
                     string(credentialsId: 'benchmark-metrics-datastore-nlb-endpoint', variable: 'DATASTORE_ENDPOINT')]) {
        def curlCommand = """
              curl -X POST "https://${DATASTORE_ENDPOINT}/benchmark-results-*/_search" -ku ${DATASTORE_USER}:${DATASTORE_PASSWORD} -H 'Content-Type: application/json' -d '{
              "size": 1,
              "query": {
                "bool": {
                  "must": [
                    {
                      "term": {
                        "user-tags.cluster-config": \"${baselineClusterConfig}\"
                      }
                    },
                    {
                      "term": {
                        "workload": \"${workload}\"
                      }
                    },
                    {
                      "term": {
                        "distribution-version": \"${distributionVersion}\"
                      }
                    },
                    {
                      "range": {
                        "test-execution-timestamp": {
                          "gte": "now-5d/d",
                          "lte": "now/d"
                        }
                      }
                    }
                  ]
                }
              },
              "sort": [
                {
                  "test-execution-timestamp": {
                    "order": "desc"
                  }
                }
              ],
              "_source": ["test-execution-id"]
            }'
        """

        String output = sh(script: curlCommand, returnStdout: true).trim()

        return processQueryOutput(output)
    }
}

String getContenderTestExecutionId(pullRequestNumber) {
    withCredentials([string(credentialsId: 'benchmark-metrics-datastore-user', variable: 'DATASTORE_USER'),
                     string(credentialsId: 'benchmark-metrics-datastore-password', variable: 'DATASTORE_PASSWORD'),
                     string(credentialsId: 'benchmark-metrics-datastore-nlb-endpoint', variable: 'DATASTORE_ENDPOINT')]) {
        def curlCommand = """
              curl -X POST "https://${DATASTORE_ENDPOINT}/benchmark-results-*/_search" -ku ${DATASTORE_USER}:${DATASTORE_PASSWORD} -H 'Content-Type: application/json' -d '{
              "size": 1,
              "query": {
                "bool": {
                  "must": [
                    {
                      "term": {
                        "user-tags.pull_request_number": \"${pullRequestNumber}\"
                      }
                    }
                  ]
                }
              },
              "sort": [
                {
                  "test-execution-timestamp": {
                    "order": "desc"
                  }
                }
              ],
              "_source": ["test-execution-id"]
            }'
        """

        String output = sh(script: curlCommand, returnStdout: true).trim()
        return processQueryOutput(output)

    }
}

String processQueryOutput(output) {
    def slurper = new JsonSlurper()
    def result = slurper.parseText(output)

    if (result.hits.total.value > 0) {
        String testExecutionId = result.hits.hits[0]._source['test-execution-id']
        echo "Latest test-execution-id: ${testExecutionId}"
        return testExecutionId
    } else {
        echo "No matching documents found"
        return null
    }
}

boolean isNullOrEmpty(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }
