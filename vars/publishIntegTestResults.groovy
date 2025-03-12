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
 * @param args.jobName <required> - The integ test job name, used in `testReportManifestYmlUrl`.
 */

import groovy.json.JsonOutput
import java.text.SimpleDateFormat
import java.util.Date

void call(Map args = [:]) {


    // To ensure the test TestOpenSearchIntegTest from opensearch-build repo passes.
    def isNullOrEmpty = { str ->
        str == null || (str instanceof String && str.trim().isEmpty())
    }
    // Check if any args is equals to null or it is a test run
    if (isNullOrEmpty(args.distributionBuildUrl) || isNullOrEmpty(args.testReportManifestYml) || isNullOrEmpty(args.jobName) || args.jobName.equals('dummy_job')) {
        return null
    }

    def integTestBuildNumber = currentBuild.number
    def integTestBuildUrl = env.RUN_DISPLAY_URL
    def distributionBuildUrl = args.distributionBuildUrl
    def buildStartTime = currentBuild.startTimeInMillis
    def currentDate = new Date()
    def formattedDate = new SimpleDateFormat("MM-yyyy").format(currentDate)
    def testReportManifestYml = args.testReportManifestYml
    def jobName = args.jobName
    def manifestFile = readFile testReportManifestYml
    def manifest = readYaml text: manifestFile
    def indexName = "opensearch-integration-test-results-${formattedDate}"
    def testFailuresindexName = "opensearch-integration-test-failures-${formattedDate}"
    def finalJsonDoc = ""
    // Qualifier is in-built in the version. Splitting it until https://github.com/opensearch-project/opensearch-build/issues/5386 is resolved
    def versionTokenize = manifest.version.tokenize('-')
    def version = versionTokenize[0]
    def qualifier = versionTokenize[1] ?: "None"
    def distributionBuildNumber = manifest.id
    def rcNumber = manifest.rc.toInteger()
    def rc = (rcNumber > 0)
    def platform = manifest.platform
    def architecture = manifest.architecture
    def distribution = manifest.distribution
    def testReportManifestYmlUrl = "https://ci.opensearch.org/ci/dbc/${jobName}/${version}/${distributionBuildNumber}/${platform}/${architecture}/${distribution}/test-results/${integTestBuildNumber}/integ-test/test-report.yml"

    manifest.components.each { component ->
        def componentName = component.name
        def componentRepo = component.repository.split('/')[-1].replace('.git', '')
        def componentRepoUrl = component.repository.substring(component.repository.indexOf("github.com")).replace(".git", "")
        def componentCategory = manifest.name
        def withSecurity = component.configs.find { it.name == 'with-security' }?.status?.toLowerCase() ?: 'unknown'
        def withoutSecurity = component.configs.find { it.name == 'without-security' }?.status?.toLowerCase() ?: 'unknown'
        def componentResult = (withSecurity == 'fail' || withoutSecurity == 'fail' || withSecurity == 'Not Available' || withoutSecurity == 'Not Available') ? 'failed' : 'passed'
        def withSecurityYml = component.configs.find { it.name == 'with-security' }?.yml ?: ''
        def withSecurityClusterStdout = component.configs.find { it.name == 'with-security' }?.cluster_stdout ?: []
        def withSecurityClusterStderr = component.configs.find { it.name == 'with-security' }?.cluster_stderr ?: []
        def withSecurityTestStdout = component.configs.find { it.name == 'with-security' }?.test_stdout ?: ''
        def withSecurityTestStderr = component.configs.find { it.name == 'with-security' }?.test_stderr ?: ''
        def withoutSecurityYml = component.configs.find { it.name == 'without-security' }?.yml ?: ''
        def withoutSecurityClusterStdout = component.configs.find { it.name == 'without-security' }?.cluster_stdout ?: []
        def withoutSecurityClusterStderr = component.configs.find { it.name == 'without-security' }?.cluster_stderr ?: []
        def withoutSecurityTestStdout = component.configs.find { it.name == 'without-security' }?.test_stdout ?: ''
        def withoutSecurityTestStderr = component.configs.find { it.name == 'without-security' }?.test_stderr ?: ''
        def withSecurityFailedTests = component.configs.find { it.name == 'with-security' }?.failed_test ?: []
        processFailedTests(withSecurityFailedTests, componentName, componentRepo, componentRepoUrl, version, qualifier, integTestBuildNumber,
                   integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber,
                   platform, architecture, distribution, componentCategory, "with-security", testFailuresindexName)
        def withoutSecurityFailedTests = component.configs.find { it.name == 'without-security' }?.failed_test ?: []
        processFailedTests(withoutSecurityFailedTests, componentName, componentRepo, componentRepoUrl, version, qualifier, integTestBuildNumber,
                   integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber,
                   platform, architecture, distribution, componentCategory, "without-security", testFailuresindexName)
        def jsonContent = generateJson(
                                        componentName, componentRepo, componentRepoUrl, version, qualifier, integTestBuildNumber,
                                        integTestBuildUrl, distributionBuildNumber, distributionBuildUrl,
                                        buildStartTime, rc, rcNumber,
                                        platform, architecture, distribution,
                                        componentCategory, componentResult, testReportManifestYmlUrl,
                                        withSecurity, withSecurityYml, withSecurityClusterStdout,
                                        withSecurityClusterStderr, withSecurityTestStdout, withSecurityTestStderr,
                                        withoutSecurity, withoutSecurityYml, withoutSecurityClusterStdout, withoutSecurityClusterStderr,
                                        withoutSecurityTestStdout, withoutSecurityTestStderr
                                    )
        finalJsonDoc += "{\"index\": {\"_index\": \"${indexName}\"}}\n" + "${jsonContent}\n"
    }
    writeFile file: "test-records.json", text: finalJsonDoc
    def fileContents = readFile(file: "test-records.json").trim()
    indexFailedTestData(indexName, "test-records.json")
}

def processFailedTests(failedTests, componentName, componentRepo, componentRepoUrl, version, qualifier, integTestBuildNumber,
                       integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime,
                       rc, rcNumber, platform, architecture, distribution, componentCategory, securityType, testFailuresindexName) {

    def finalFailedTestsJsonDoc = ""
    switch (true) {
        case failedTests.isEmpty():
            break
        case failedTests.contains("Test Result Not Available"):
            def testResultJsonContent = generateFailedTestJson(componentName, componentRepo, componentRepoUrl, version, qualifier, integTestBuildNumber,
                integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber,
                platform, architecture, distribution, componentCategory, securityType, "Result Not Available", "Result Not Available")
            finalFailedTestsJsonDoc += "{\"index\": {\"_index\": \"${testFailuresindexName}\"}}\n${testResultJsonContent}\n"
            break
        case failedTests.contains("Test Result Files List Not Available"):
            def testResultJsonContent = generateFailedTestJson(componentName, componentRepo, componentRepoUrl, version, qualifier,integTestBuildNumber,
                integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber,
                platform, architecture, distribution, componentCategory, securityType, "Report Not Available", "Report Not Available")
            finalFailedTestsJsonDoc += "{\"index\": {\"_index\": \"${testFailuresindexName}\"}}\n${testResultJsonContent}\n"
            break
        case failedTests.contains("No Failed Test"):
            break
        default:
            failedTests.collect { failedTest ->
                def match = failedTest.split("#")
                if (match) {
                    def testResultJsonContent = generateFailedTestJson(componentName, componentRepo, componentRepoUrl, version, qualifier, integTestBuildNumber,
                        integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber,
                        platform, architecture, distribution, componentCategory, securityType, match[0].trim(), match[1].trim())
                    finalFailedTestsJsonDoc += "{\"index\": {\"_index\": \"${testFailuresindexName}\"}}\n${testResultJsonContent}\n"
                }
            }
            break
    }
    if (!finalFailedTestsJsonDoc.isEmpty()) {
        writeFile file: "test-failures.json", text: finalFailedTestsJsonDoc
        def fileContents = readFile(file: "test-failures.json").trim()
        indexTestFailuresData(testFailuresindexName, "test-failures.json")
    }
    return finalFailedTestsJsonDoc
}


boolean argCheck(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }

void indexTestFailuresData(testFailuresindexName, testFailuresFile) {
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
                    echo "INDEX NAME IS ${testFailuresindexName}"
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
                                "version": {
                                    "type": "keyword"
                                },
                                "qualifier": {
                                    "type": "keyword"
                                },
                                "integ_test_build_number": {
                                    "type": "integer"
                                },
                                "integ_test_build_url": {
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
                                "platform": {
                                    "type": "keyword"
                                },
                                "architecture": {
                                    "type": "keyword"
                                },
                                "distribution": {
                                    "type": "keyword"
                                },
                                "component_category": {
                                    "type": "keyword"
                                },
                                "test_type": {
                                    "type": "keyword"
                                },
                                "test_class": {
                                    "type": "keyword"
                                },
                                "test_name": {
                                    "type": "keyword"
                                }
                            }
                        }
                    }'
                    curl -I "${METRICS_HOST_URL}/${testFailuresindexName}" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" | grep -E "HTTP\\/[0-9]+(\\.[0-9]+)? 200"
                    if [ \$? -eq 0 ]; then
                        echo "Index already exists. Indexing Results"
                    else
                        echo "Index does not exist. Creating..."
                        create_index_response=\$(curl -s -XPUT "${METRICS_HOST_URL}/${testFailuresindexName}" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" -H 'Content-Type: application/json' -d "\${INDEX_MAPPING}")
                        if [[ \$create_index_response == *'"acknowledged":true'* ]]; then
                            echo "Index created successfully."
                            echo "Updating alias..."
                            update_alias_response=\$(curl -s -XPOST "${METRICS_HOST_URL}/_aliases" --aws-sigv4 "aws:amz:us-east-1:es" --user "${awsAccessKey}:${awsSecretKey}" -H "x-amz-security-token:${awsSessionToken}" -H "Content-Type: application/json" -d '{
                                "actions": [
                                    {
                                        "add": {
                                        "index": "${testFailuresindexName}",
                                        "alias": "opensearch-integration-test-failures"
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
                    if [ -s ${testFailuresFile} ]; then
                        echo "File Exists, indexing failed tests."
                        curl -XPOST "${METRICS_HOST_URL}/$testFailuresindexName/_bulk" --aws-sigv4 \"aws:amz:us-east-1:es\" --user \"${awsAccessKey}:${awsSecretKey}\" -H \"x-amz-security-token:${awsSessionToken}\" -H "Content-Type: application/x-ndjson" --data-binary "@${testFailuresFile}"
                    else
                        echo "File Does not exist. No tests records to process."
                    fi
            """
            }
        }
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
                            "version": {
                                "type": "keyword"
                            },
                            "qualifier": {
                                    "type": "keyword"
                            },
                            "integ_test_build_number": {
                                "type": "integer"
                            },
                            "integ_test_build_url": {
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
                            "platform": {
                                "type": "keyword"
                            },
                            "architecture": {
                                "type": "keyword"
                            },
                            "distribution": {
                                "type": "keyword"
                            },
                            "component_category": {
                                "type": "keyword"
                            },
                            "component_build_result": {
                                "type": "keyword"
                            },
                            "test_report_manifest_yml": {
                                "type": "keyword"
                            },
                            "with_security": {
                                "type": "keyword"
                            },
                            "with_security_build_yml": {
                                "type": "keyword"
                            },
                            "with_security_cluster_stdout": {
                                "type": "keyword"
                            },
                            "with_security_test_stdout": {
                                "type": "keyword"
                            },
                            "with_security_cluster_stderr": {
                                "type": "keyword"
                            },
                            "with_security_test_stderr": {
                                "type": "keyword"
                            },
                            "without_security": {
                                "type": "keyword"
                            },
                            "without_security_build_yml": {
                                "type": "keyword"
                            },
                            "without_security_cluster_stdout": {
                                "type": "keyword"
                            },
                            "without_security_test_stdout": {
                                "type": "keyword"
                            },
                            "without_security_cluster_stderr": {
                                "type": "keyword"
                            },
                            "without_security_test_stderr": {
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
                                    "alias": "opensearch-integration-test-results"
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

def generateFailedTestJson(componentName, componentRepo, componentRepoUrl, version, qualifier,
                integTestBuildNumber, integTestBuildUrl, distributionBuildNumber, distributionBuildUrl,
                buildStartTime, rc, rcNumber, platform, architecture, distribution, componentCategory,
                testType, testClass, testName) {
    def json = [
        component: componentName,
        component_repo: componentRepo,
        component_repo_url: componentRepoUrl,
        version: version,
        qualifier: qualifier,
        integ_test_build_number: integTestBuildNumber,
        integ_test_build_url: integTestBuildUrl,
        distribution_build_number: distributionBuildNumber,
        distribution_build_url: distributionBuildUrl,
        build_start_time: buildStartTime,
        rc: rc,
        rc_number: rcNumber,
        platform: platform,
        architecture: architecture,
        distribution: distribution,
        component_category: componentCategory,
        test_type: testType,
        test_class: testClass,
        test_name: testName
    ]
    return JsonOutput.toJson(json)
}


def generateJson(componentName, componentRepo, componentRepoUrl, version, qualifier,
                integTestBuildNumber, integTestBuildUrl, distributionBuildNumber, distributionBuildUrl,
                buildStartTime, rc, rcNumber, platform, architecture, distribution, componentCategory,
                componentResult, testReportManifestYmlUrl, withSecurity, withSecurityYml, withSecurityClusterStdout,
                withSecurityClusterStderr, withSecurityTestStdout,withSecurityTestStderr, withoutSecurity,
                withoutSecurityYml, withoutSecurityClusterStdout, withoutSecurityClusterStderr, withoutSecurityTestStdout,
                withoutSecurityTestStderr) {
    def json = [
        component: componentName,
        component_repo: componentRepo,
        component_repo_url: componentRepoUrl,
        version: version,
        qualifier: qualifier,
        integ_test_build_number: integTestBuildNumber,
        integ_test_build_url: integTestBuildUrl,
        distribution_build_number: distributionBuildNumber,
        distribution_build_url: distributionBuildUrl,
        build_start_time: buildStartTime,
        rc: rc,
        rc_number: rcNumber,
        platform: platform,
        architecture: architecture,
        distribution: distribution,
        component_category: componentCategory,
        component_build_result: componentResult,
        test_report_manifest_yml: testReportManifestYmlUrl,
        with_security: withSecurity,
        with_security_build_yml: withSecurityYml,
        with_security_cluster_stdout: withSecurityClusterStdout,
        with_security_cluster_stderr: withSecurityClusterStderr,
        with_security_test_stdout: withSecurityTestStdout,
        with_security_test_stderr: withSecurityTestStderr,
        without_security: withoutSecurity,
        without_security_build_yml: withoutSecurityYml,
        without_security_cluster_stdout: withoutSecurityClusterStdout,
        without_security_cluster_stderr: withoutSecurityClusterStderr,
        without_security_test_stdout: withoutSecurityTestStdout,
        without_security_test_stderr: withoutSecurityTestStderr
    ]
    return JsonOutput.toJson(json)
}
