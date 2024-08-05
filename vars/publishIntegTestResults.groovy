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
    if (isNullOrEmpty(args.version) || isNullOrEmpty(args.distributionBuildNumber) || isNullOrEmpty(args.distributionBuildUrl) || 
        isNullOrEmpty(args.rcNumber) || isNullOrEmpty(args.rc) || isNullOrEmpty(args.platform) || 
        isNullOrEmpty(args.architecture) || isNullOrEmpty(args.distribution) || isNullOrEmpty(args.testReportManifestYml) || isNullOrEmpty(args.jobName)) {
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
    def testReportManifestYmlUrl = "https://ci.opensearch.org/ci/dbc/${jobName}/${version}/${distributionBuildNumber}/${platform}/${architecture}/${distribution}/test-results/${integTestBuildNumber}/integ-test/test-report.yml"
    def manifestFile = readFile testReportManifestYml
    def manifest = readYaml text: manifestFile
    def indexName = "opensearch-integration-test-results-${formattedDate}"
    def finalJsonDoc = ""
    def version = manifest.version.toString()
    def distributionBuildNumber = manifest.id
    def rcNumber = manifest.rc.toInteger()
    def rc = (rcNumber > 0)
    def platform = manifest.platform
    def architecture = manifest.architecture
    def distribution = manifest.distribution

    manifest.components.each { component ->
        def componentName = component.name
        def componentCategory = manifest.name
        def withSecurity = component.configs.find { it.name == 'with-security' }?.status?.toLowerCase() ?: 'unknown'
        def withoutSecurity = component.configs.find { it.name == 'without-security' }?.status?.toLowerCase() ?: 'unknown'
        def componentResult = (withSecurity == 'fail' || withoutSecurity == 'fail' || withSecurity == 'Not Available' || withoutSecurity == 'Not Available') ? 'failed' : 'passed'
        def withSecurityYml = component.configs.find { it.name == 'with-security' }?.yml ?: ''
        def withSecurityStdout = component.configs.find { it.name == 'with-security' }?.cluster_stdout ?: []
        def withSecurityStderr = component.configs.find { it.name == 'with-security' }?.cluster_stderr ?: []
        def withoutSecurityYml = component.configs.find { it.name == 'without-security' }?.yml ?: ''
        def withoutSecurityStdout = component.configs.find { it.name == 'without-security' }?.cluster_stdout ?: []
        def withoutSecurityStderr = component.configs.find { it.name == 'without-security' }?.cluster_stderr ?: []
        def jsonContent = generateJson(
                                        componentName, version, integTestBuildNumber, 
                                        integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, 
                                        buildStartTime, rc, rcNumber, 
                                        platform, architecture, distribution, 
                                        componentCategory, componentResult, testReportManifestYmlUrl, 
                                        withSecurity, withSecurityYml, withSecurityStdout, 
                                        withSecurityStderr, withoutSecurity, withoutSecurityYml, 
                                        withoutSecurityStdout, withoutSecurityStderr
                                    )
        finalJsonDoc += "{\"index\": {\"_index\": \"${indexName}\"}}\n" + "${jsonContent}\n"
    }
    writeFile file: "test-records.json", text: finalJsonDoc
    def fileContents = readFile(file: "test-records.json").trim()
    indexFailedTestData(indexName, "test-records.json")
}

boolean argCheck(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }

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
                            "version": {
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
                            "with_security_cluster_stderr": {
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
                            "without_security_cluster_stderr": {
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

def generateJson(component, version, integTestBuildNumber, integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber, platform, architecture, distribution, componentCategory, componentResult, testReportManifestYmlUrl, withSecurity, withSecurityYml, withSecurityStdout, withSecurityStderr, withoutSecurity, withoutSecurityYml, withoutSecurityStdout, withoutSecurityStderr) {
    def json = [
        component: component,
        version: version,
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
        with_security_cluster_stdout: withSecurityStdout,
        with_security_cluster_stderr: withSecurityStderr,
        without_security: withoutSecurity,
        without_security_build_yml: withoutSecurityYml,
        without_security_cluster_stdout: withoutSecurityStdout,
        without_security_cluster_stderr: withoutSecurityStderr
    ]
    return JsonOutput.toJson(json)
}

