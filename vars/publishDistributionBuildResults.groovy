/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to fetch the failing Integration test details at the end of Integration Test Jenkins build and index the results to OpenSearch Metrics cluster.
 *
 * @param Map args = [:] args A map of the following parameters.
 * @param args.rc <required> - If the integration tests are running on an RC. 
 * @param args.rcNumber <required> - The RC number against which the integration test is executed.
 * @param args.failureMessages <required> - Failure message retrieved from buildFailureMessage() method.
 * @param args.passMessages <required> - Passing message retrieved from buildFailureMessage() method. Used to get the passed components list.
 * @param args.componentCategory <required> - The OpenSearch or OpenSearch Dashboards plugin
 * @param args.inputManifestPath <required> - Path to input manifest.
 */

import groovy.json.JsonOutput
import java.text.SimpleDateFormat
import java.util.Date

void call(Map args = [:]) {
    def distributionBuildNumber = currentBuild.number
    def distributionBuildUrl = env.RUN_DISPLAY_URL
    def buildStartTime = currentBuild.startTimeInMillis
    def currentDate = new Date()
    def formattedDate = new SimpleDateFormat("MM-yyyy").format(currentDate)
    def rc = args.rc
    def rcNumber = args.rcNumber.toInteger()
    def indexName = "opensearch-distribution-build-results-${formattedDate}"
    def failureMessages = args.failureMessages
    def passMessages = args.passMessages
    def componentCategory = args.componentCategory
    def inputManifest = readYaml(file: args.inputManifestPath)
    def version = inputManifest.build.version
    def finalJsonDoc = ""
    List<String> failedComponents = extractComponents(failureMessages, /(?<=\bError building\s).*/, 0)
    List<String> passedComponents = extractComponents(passMessages, /(?<=\bSuccessfully built\s).*/, 0)
    inputManifest.components.each { component ->
        if (failedComponents.contains(component.name)) {
            def jsonData = generateAndAppendJson(component.name, component.repository.split('/')[-1].replace('.git', ''), component.repository.substring(component.repository.indexOf("github.com")).replace(".git", ""), component.ref,
                                version, distributionBuildNumber, distributionBuildUrl,
                                buildStartTime, rc, rcNumber, componentCategory, "failed"
                                )
            finalJsonDoc += "{\"index\": {\"_index\": \"${indexName}\"}}\n${jsonData}\n"
        } else if (passedComponents.contains(component.name)) {
            def jsonData = generateAndAppendJson(component.name, component.repository.split('/')[-1].replace('.git', ''), component.repository.substring(component.repository.indexOf("github.com")).replace(".git", ""), component.ref,
                                version, distributionBuildNumber, distributionBuildUrl,
                                buildStartTime, rc, rcNumber, componentCategory, "passed"
                                )
            finalJsonDoc += "{\"index\": {\"_index\": \"${indexName}\"}}\n${jsonData}\n"
        }
    }
    writeFile file: "test-records.json", text: finalJsonDoc
    def fileContents = readFile(file: "test-records.json").trim()
    indexFailedTestData(indexName, "test-records.json")

}

void indexFailedTestData(indexName, testRecordsFile) {
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
                echo "INDEX NAME IS ${indexName}"
                INDEX_MAPPING='{
                    "mappings": {
                        "properties": {
                            "component": {
                                "type": "keyword"
                            },
                            "component_repo": {
                                "type": "keyword"
                            },
                           "component_repo_url": {
                                "type": "keyword"
                            },
                            "component_ref": {
                                "type": "keyword"
                            },
                            "version": {
                                "type": "keyword"
                            },
                            "distribution_build_number": {
                                "type": "integer"
                            },
                            "distribution_build_url": {
                                "type": "keyword"
                            },
                            "build_start_time": {
                                "type": "date",
                                "format": "epoch_millis"
                            },
                            "rc": {
                                "type": "keyword"
                            },
                            "rc_number": {
                                "type": "integer"
                            },
                            "component_category": {
                                "type": "keyword"
                            },
                            "component_build_result": {
                                "type": "keyword"
                            }
                        }
                    }
                }'
                curl -I "${METRICS_HOST_URL}/${indexName}" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" | grep -E "HTTP\\/[0-9]+(\\.[0-9]+)? 200"
                if [ \$? -eq 0 ]; then
                    echo "Index already exists. Indexing Results"
                else
                    echo "Index does not exist. Creating..."
                    create_index_response=\$(curl -s -XPUT "${METRICS_HOST_URL}/${indexName}" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" -H 'Content-Type: application/json' -d "\${INDEX_MAPPING}")
                    if [[ \$create_index_response == *'"acknowledged":true'* ]]; then
                        echo "Index created successfully."
                        echo "Updating alias..."
                        update_alias_response=\$(curl -s -XPOST "${METRICS_HOST_URL}/_aliases" --aws-sigv4 "aws:amz:us-east-1:es" --user "${awsAccessKey}:${awsSecretKey}" -H "x-amz-security-token:${awsSessionToken}" -H "Content-Type: application/json" -d '{
                            "actions": [
                                {
                                    "add": {
                                    "index": "${indexName}",
                                    "alias": "opensearch-distribution-build-results"
                                    }
                                }
                            ]
                        }')
                        if [[ \$update_alias_response == *'"acknowledged":true'* ]]; then
                            echo "Alias updated successfully."
                        else
                            echo "Failed to update alias. Error message: \$update_alias_response"
                        fi
                    else
                        echo "Failed to create index. Error message: \$create_index_response"
                        exit 1
                    fi
                fi
                if [ -s ${testRecordsFile} ]; then
                    echo "File Exists, indexing results."
                    curl -XPOST "${METRICS_HOST_URL}/$indexName/_bulk" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" -H "Content-Type: application/x-ndjson" --data-binary "@${testRecordsFile}"
                else
                    echo "File Does not exist. No tests records to process."
                fi
        """
        }
    }
}

def generateJson(component, componentRepo, componentRepoUrl, componentRef, version, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber, componentCategory, componentResult) {
    def json = [
        component: component,
        component_repo: componentRepo,
        component_repo_url: componentRepoUrl,
        component_ref: componentRef,
        version: version,
        distribution_build_number: distributionBuildNumber,
        distribution_build_url: distributionBuildUrl,
        build_start_time: buildStartTime,
        rc: rc,
        rc_number: rcNumber,
        component_category: componentCategory,
        component_build_result: componentResult,
    ]
    return JsonOutput.toJson(json)
}

def generateAndAppendJson(component, componentRepo, componentRepoUrl, componentRef, version, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber, componentCategory, status) {
    def jsonData = generateJson(
        component, componentRepo, componentRepoUrl, componentRef, version, 
        distributionBuildNumber, distributionBuildUrl, buildStartTime, 
        rc, rcNumber, componentCategory, status
    )
    return jsonData
}

def extractComponents(List<String> messages, String regex, int splitIndex) {
    List<String> components = []
    for (message in messages) {
        java.util.regex.Matcher match = (message =~ regex)
        String matched = match[0]
        components.add(matched.split(' ')[0].split(',')[splitIndex].trim())
    }
    return components.unique()
}
