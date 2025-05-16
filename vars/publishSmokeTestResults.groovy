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
 * @param args.distributionBuildUrl <required> - The jenkins distribution build number.
 * @param args.testReportManifestYml <required> - The generated test report YAML file using test report workflow.
 * @param args.jobName <required> - The smoke test job name, used in `testReportManifestYmlUrl`.
 */

import groovy.json.JsonOutput
import java.text.SimpleDateFormat
import java.util.Date

void call(Map args = [:]) {

    def isNullOrEmpty = { str ->
        str == null || (str instanceof String && str.trim().isEmpty())
    }
    // Check if any args is equals to null or it is a test run
    if (isNullOrEmpty(args.distributionBuildUrl) || isNullOrEmpty(args.testReportManifestYml) || isNullOrEmpty(args.jobName) || args.jobName.equals('dummy_job')) {
        return null
    }

    def smokeTestBuildNumber = currentBuild.number
    def smokeTestBuildUrl = env.RUN_DISPLAY_URL
    def distributionBuildUrl = args.distributionBuildUrl
    def buildStartTime = currentBuild.startTimeInMillis
    def currentDate = new Date()
    def formattedDate = new SimpleDateFormat("MM-yyyy").format(currentDate)
    def testReportManifestYml = args.testReportManifestYml
    def jobName = args.jobName
    def manifestFile = readFile testReportManifestYml
    def manifest = readYaml text: manifestFile
    def indexName = "opensearch-smoke-test-results-${formattedDate}"
    def finalJsonDoc = ""
    def fullVersion = manifest.version
    def versionTokenize = fullVersion.tokenize('-')
    def version = versionTokenize[0]
    def qualifier = versionTokenize[1] ?: "None"
    def distributionBuildNumber = manifest.id
    def rcNumber = manifest.rc.toInteger()
    def rc = (rcNumber > 0)
    def platform = manifest.platform
    def architecture = manifest.architecture
    def distribution = manifest.distribution
    def testReportManifestYmlUrl = "https://ci.opensearch.org/ci/dbc/${jobName}/${fullVersion}/${distributionBuildNumber}/${platform}/${architecture}/${distribution}/test-results/${smokeTestBuildNumber}/smoke-test/test-report.yml"

    manifest.components.each { component ->
        def componentName = component.name
        def componentRepo = component.repository.split('/')[-1].replace('.git', '')
        def componentRepoUrl = component.repository.substring(component.repository.indexOf("github.com")).replace(".git", "")
        def componentCategory = manifest.name

        if (component.configs) {
            component.configs.each { config ->
                def componentTestSpecName = config.name

                def componentTestSpecResult = config.status
                def componentTestSpecYml = config.yml
                def componentTestSpecClusterStdout = config.cluster_stdout?.join(', ')
                def componentTestSpecClusterStderr = config.cluster_stderr?.join(', ')
                def componentTestSpecTestStdout = config.test_stdout
                def componentTestSpecTestStderr = config.test_stderr

                def jsonContent = generateJson(componentName, componentRepo, componentRepoUrl, version, qualifier,
                        smokeTestBuildNumber, smokeTestBuildUrl, distributionBuildNumber, distributionBuildUrl,
                        buildStartTime, rc, rcNumber, platform, architecture, distribution, componentCategory,
                        componentTestSpecResult, testReportManifestYmlUrl, componentTestSpecName, componentTestSpecYml,
                        componentTestSpecClusterStdout, componentTestSpecClusterStderr, componentTestSpecTestStdout,
                        componentTestSpecTestStderr)
                finalJsonDoc += "{\"index\": {\"_index\": \"${indexName}\"}}\n" + "${jsonContent}\n"
            }
        }
        writeFile file: "test-records.json", text: finalJsonDoc
        def fileContents = readFile(file: "test-records.json").trim()
        indexSmokeTestData(indexName, "test-records.json")

    }
}

boolean argCheck(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }

void indexSmokeTestData(indexName, testRecordsFile) {
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
                            "architecture": {
                                "type": "keyword"
                            },
                            "build_start_time": {
                                "format": "epoch_millis",
                                "type": "date"
                            },
                            "component": {
                                "type": "keyword"
                            },
                            "component_category": {
                                "type": "keyword"
                            },
                            "component_repo": {
                                "type": "keyword"
                            },
                            "component_repo_url": {
                                "type": "keyword"
                            },
                            "distribution": {
                                "type": "keyword"
                            },
                            "distribution_build_number": {
                                "type": "integer"
                            },
                            "distribution_build_url": {
                                "type": "keyword"
                            },
                            "platform": {
                                "type": "keyword"
                            },
                            "qualifier": {
                                "type": "keyword"
                            },
                            "rc": {
                                "type": "keyword"
                            },
                            "rc_number": {
                                "type": "integer"
                            },
                            "smoke_test_build_number": {
                                "type": "integer"
                            },
                            "smoke_test_build_url": {
                                "type": "keyword"
                            },
                            "test_report_manifest_yml": {
                                "type": "keyword"
                            },
                            "version": {
                                "type": "keyword"
                            },
                            "component_test_spec_name": {
                                "type": "keyword"
                            },
                            "component_test_spec_result": {
                                "type": "keyword"
                            },
                            "component_test_spec_yml": {
                                "type": "keyword"
                            },
                            "component_test_spec_test_stderr": {
                                "type": "keyword"
                            },
                            "component_test_spec_test_stdout": {
                                "type": "keyword"
                            },
                            "component_test_spec_cluster_stderr": {
                                "type": "keyword"
                            },
                            "component_test_spec_cluster_stdout": {
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
                                    "alias": "opensearch-smoke-test-results"
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

def generateJson(componentName, componentRepo, componentRepoUrl, version, qualifier,
                 smokeTestBuildNumber, smokeTestBuildUrl, distributionBuildNumber, distributionBuildUrl,
                 buildStartTime, rc, rcNumber, platform, architecture, distribution, componentCategory,
                 componentTestSpecResult, testReportManifestYmlUrl, componentTestSpecName, componentTestSpecYml,
                 componentTestSpecClusterStdout, componentTestSpecClusterStderr, componentTestSpecTestStdout,
                 componentTestSpecTestStderr) {
    def json = [
        component: componentName,
        component_repo: componentRepo,
        component_repo_url: componentRepoUrl,
        version: version,
        qualifier: qualifier,
        smoke_test_build_number: smokeTestBuildNumber,
        smoke_test_build_url: smokeTestBuildUrl,
        distribution_build_number: distributionBuildNumber,
        distribution_build_url: distributionBuildUrl,
        build_start_time: buildStartTime,
        rc: rc,
        rc_number: rcNumber,
        platform: platform,
        architecture: architecture,
        distribution: distribution,
        component_category: componentCategory,
        test_report_manifest_yml: testReportManifestYmlUrl,
        component_test_spec_name: componentTestSpecName,
        component_test_spec_yml: componentTestSpecYml,
        component_test_spec_result: componentTestSpecResult,
        component_test_spec_cluster_stdout: componentTestSpecClusterStdout,
        component_test_spec_cluster_stderr: componentTestSpecClusterStderr,
        "component_test_spec_test_stdout": componentTestSpecTestStdout,
        component_test_spec_test_stderr: componentTestSpecTestStderr,
    ]
    return JsonOutput.toJson(json)
}
