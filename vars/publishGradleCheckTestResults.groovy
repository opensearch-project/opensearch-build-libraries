/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to fetch failing tests at the end of gradle-check run and index the results in an OpenSearch cluster.
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.prNumber <required> - The pull_request number that triggered the gradle-check run. If Null then use post_merge_action string.
 * @param args.prDescription <required> - The subject of the pull_request. If prNumber is null then it signifies push action on branch.
 */

import hudson.tasks.test.AbstractTestResultAction
import groovy.json.JsonOutput
import java.text.SimpleDateFormat
import java.util.Date

void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    def finalJsonDoc = ""
    def buildNumber = currentBuild.number
    def buildDescription = currentBuild.description
    def buildDuration = currentBuild.duration
    def buildResult = currentBuild.result
    def buildStartTime = currentBuild.startTimeInMillis
    def prNumber = args.prNumber.toString()
    def invokeType = args.invokeType.toString()
    def prOwner = args.prOwner.toString()
    def prTitle = args.prTitle.toString()
    def gitReference = args.gitReference.toString()
    def currentDate = new Date()
    def formattedDate = new SimpleDateFormat("MM-yyyy").format(currentDate)

    def indexName = "gradle-check-${formattedDate}"

    def test_docs = getFailedTestRecords(buildNumber, prNumber, invokeType, prOwner, prTitle, gitReference, buildResult, buildDuration, buildStartTime)

    if (test_docs) {
        for (doc in test_docs) {
            def jsonDoc = JsonOutput.toJson(doc)
            finalJsonDoc += "{\"index\": {\"_index\": \"${indexName}\"}}\n" + "${jsonDoc}\n"
        }
        writeFile file: "failed-test-records.json", text: finalJsonDoc

        def fileContents = readFile(file: "failed-test-records.json").trim()
        println("File Content is:\n${fileContents}")
        indexFailedTestData()
    }
}

List<Map<String, String>> getFailedTestRecords(buildNumber, prNumber, invokeType, prOwner, prTitle, gitReference, buildResult, buildDuration, buildStartTime) {
    def testResults = []
    AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
    if (testResultAction != null) {
        def testsTotal = testResultAction.totalCount
        def testsFailed = testResultAction.failCount
        def testsSkipped = testResultAction.skipCount
        def testsPassed = testsTotal - testsFailed - testsSkipped
        def failedTests = testResultAction.getFailedTests()

        if (failedTests){
            for (test in failedTests) {
                def failDocument = ['build_number': buildNumber, 'pull_request': prNumber, 'pull_request_owner': prOwner , 'invoke_type': invokeType, 'pull_request_title': prTitle, 'git_reference': gitReference, 'test_class': test.getParent().getName(), 'test_name': test.fullName, 'test_status': 'FAILED', 'build_result': buildResult, 'test_fail_count': testsFailed, 'test_skipped_count': testsSkipped, 'test_passed_count': testsPassed, 'build_duration': buildDuration, 'build_start_time': buildStartTime]
                testResults.add(failDocument)
            }
        } else {
            println("No test failed.")
        }
    }
    return testResults
}

void indexFailedTestData() {

    withCredentials([
            string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
            string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')
    ]) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN

            sh """
                set +e
                set +x
        
                MONTH_YEAR=\$(date +"%m-%Y")
                INDEX_NAME="gradle-check-\$MONTH_YEAR"
                INDEX_MAPPING='{
                    "mappings": {
                        "properties": {
                            "build_duration": {
                                "type": "float"
                            },
                            "build_number": {
                                "type": "integer"
                            },
                            "build_result": {
                                "type": "keyword"
                            },
                            "build_start_time": {
                                "type": "date"
                            },
                            "git_reference": {
                                "type": "text",
                                "fields": {
                                    "keyword": {
                                        "type": "keyword",
                                        "ignore_above": 256
                                    }
                                }
                            },
                            "invoke_type": {
                                "type": "text",
                                "fields": {
                                    "keyword": {
                                        "type": "keyword",
                                        "ignore_above": 256
                                    }
                                }
                            },
                            "pull_request": {
                                "type": "keyword"
                            },
                            "pull_request_owner": {
                                "type": "text",
                                "fields": {
                                    "keyword": {
                                        "type": "keyword",
                                        "ignore_above": 256
                                    }
                                }
                            },
                            "pull_request_title": {
                                "type": "text",
                                "fields": {
                                    "keyword": {
                                        "type": "keyword",
                                        "ignore_above": 256
                                    }
                                }
                            },
                            "test_class": {
                                "type": "keyword"
                            },
                            "test_fail_count": {
                                "type": "integer"
                            },
                            "test_name": {
                                "type": "keyword"
                            },
                            "test_passed_count": {
                                "type": "integer"
                            },
                            "test_skipped_count": {
                                "type": "integer"
                            },
                            "test_status": {
                                "type": "keyword"
                            }
                        }
                    }
                }'
                echo "INDEX NAME IS \$INDEX_NAME"
                curl -I "${METRICS_HOST_URL}/\$INDEX_NAME" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" | grep -E "HTTP\\/[0-9]+(\\.[0-9]+)? 200"
                if [ \$? -eq 0 ]; then
                    echo "Index already exists. Indexing Results"
                else
                    echo "Index does not exist. Creating..."
                    create_index_response=\$(curl -s -XPUT "${METRICS_HOST_URL}/\${INDEX_NAME}" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" -H 'Content-Type: application/json' -d "\${INDEX_MAPPING}")
                    if echo "\$create_index_response" | grep -q '"acknowledged":true'; then
                        echo "Index created successfully."
                        echo "Updating alias..."
                        update_alias_response=\$(curl -s -XPOST "${METRICS_HOST_URL}/_aliases" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" -H "Content-Type: application/json" -d '{
                            "actions": [
                                {
                                    "add": {
                                    "index": "\${INDEX_NAME}",
                                    "alias": "gradle-check"
                                    }
                                }
                            ]
                        }')
                        if echo "\$update_alias_response" | grep -q '"acknowledged":true'; then
                            echo "Alias updated successfully."
                        else
                            echo "Failed to update alias. Error message: \$update_alias_response"
                        fi
                    else
                        echo "Failed to create index. Error message: \$create_index_response"
                        exit 1
                    fi
                fi
                if [ -s failed-test-records.json ]; then
                    echo "File Exists, indexing results."
                    curl -XPOST "${METRICS_HOST_URL}/\$INDEX_NAME/_bulk" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" -H "Content-Type: application/x-ndjson" --data-binary "@failed-test-records.json"
                else
                    echo "File Does not exist. No failing test records to process."
                fi
        """
        }
    }
}

boolean isNullOrEmpty(String str) { return (str == 'Null' || str == null || str.allWhitespace || str.isEmpty()) }
