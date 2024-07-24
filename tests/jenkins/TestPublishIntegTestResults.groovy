/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 
package jenkins.tests

import org.junit.Before
import static org.mockito.Mockito.*
import groovy.json.JsonSlurper
import org.junit.Test


class TestPublishIntegTestResults extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('currentBuild', [
            number: 123,
            startTimeInMillis: System.currentTimeMillis(),
        ])
        binding.setVariable('env', [
            RUN_DISPLAY_URL: 'http://example.com/build/123'
        ])
        binding.setVariable('sh', { cmd -> println cmd })
        binding.setVariable('readFile', { filePath -> 'components:\n- name: component1\n  configs:\n    - name: with-security\n      status: pass\n    - name: without-security\n      status: fail' })
        binding.setVariable('writeFile', { params -> println params.text })
        binding.setVariable('withCredentials', { creds, closure -> closure() })
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('curl', { params -> println params })
    }

    @Test
    void testIndexFailedTestData() {
        def indexName = 'test-index'
        def testRecordsFile = 'test-records.ndjson'

        def script = loadScript('vars/publishIntegTestResults.groovy')

        def calledCommands = new ArrayList()
        script.metaClass.sh = { String command ->
            calledCommands << command
            if (command.contains("curl -I")) {
                return "HTTP/1.1 200 OK"
            } else if (command.contains("curl -s -XPUT") && command.contains("test-index")) {
                return '{"acknowledged":true}'
            } else if (command.contains("curl -XPOST") && command.contains("test-index")) {
                return '{"took":10, "errors":false}'
            } else {
                throw new IllegalArgumentException("Unexpected command: $command")
            }
        }

        script.indexFailedTestData(indexName, testRecordsFile)
        
        def expectedCommandBlock = '''set +e
        set +x
        echo "INDEX NAME IS test-index"
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
        curl -I "METRICS_HOST_URL/test-index" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" | grep -E "HTTP\\/[0-9]+(\\.[0-9]+)? 200"
        if [ $? -eq 0 ]; then
            echo "Index already exists. Indexing Results"
        else
            echo "Index does not exist. Creating..."
            create_index_response=$(curl -s -XPUT "METRICS_HOST_URL/test-index" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" -H 'Content-Type: application/json' -d "${INDEX_MAPPING}")
            if [[ $create_index_response == *'"acknowledged":true'* ]]; then
                echo "Index created successfully."
            else
                echo "Failed to create index. Error message: $create_index_response"
                exit 1
            fi
        fi
        if [ -s test-records.ndjson ]; then
            echo "File Exists, indexing results."
            curl -XPOST "METRICS_HOST_URL/test-index/_bulk" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" -H "Content-Type: application/x-ndjson" --data-binary "@test-records.ndjson"
        else
            echo "File Does not exist. No tests records to process."
        fi'''
        assert calledCommands.size() == 1 
        assert normalizeString(calledCommands[0]) == normalizeString(expectedCommandBlock)
    }


    @Test
    void testGenerateJson() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.generateJson(
            'component1', '1.0', 123,
            'http://example.com/build/123', 456, 'http://example.com/distribution/456',
            System.currentTimeMillis(), 'rc1', 1, 'linux', 'x64', 'tar', 'test-category',
            'failed', 'http://example.com/test-report.yml', 'pass', 'yml1', ['stdout1'], ['stderr1'],
            'fail', 'yml2', ['stdout2'], ['stderr2']
        )

        def parsedResult = new JsonSlurper().parseText(result)
        def expectedJson = [
            component: 'component1',
            version: '1.0',
            integ_test_build_number: 123,
            integ_test_build_url: 'http://example.com/build/123',
            distribution_build_number: 456,
            distribution_build_url: 'http://example.com/distribution/456',
            // Ignore build_start_time for comparison
            rc: 'rc1',
            rc_number: 1,
            platform: 'linux',
            architecture: 'x64',
            distribution: 'tar',
            component_category: 'test-category',
            component_build_result: 'failed',
            test_report_manifest_yml: 'http://example.com/test-report.yml',
            with_security: 'pass',
            with_security_build_yml: 'yml1',
            with_security_cluster_stdout: ['stdout1'],
            with_security_cluster_stderr: ['stderr1'],
            without_security: 'fail',
            without_security_build_yml: 'yml2',
            without_security_cluster_stdout: ['stdout2'],
            without_security_cluster_stderr: ['stderr2']
        ]

        // Remove the dynamic field for comparison
        parsedResult.remove('build_start_time')
        assert parsedResult == expectedJson
    }

    @Test
    void testCallWithMissingArgs() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def args = [
            version: "1.0",
            distributionBuildNumber: null, // Missing required argument
            distributionBuildUrl: "http://example.com/distribution/456",
            rc: "rc1",
            rcNumber: "1",
            platform: "linux",
            architecture: "x64",
            distribution: "tar",
            testReportManifestYml: "path/to/testReportManifest.yml",
            jobName: "test-job"
        ]

        def result = script.call(args)
        
        assert result == null
    }

    @Test
    void testCallWithEmptyArgs() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def args = [
            version: "1.0",
            distributionBuildNumber: "", // Empty required argument
            distributionBuildUrl: "http://example.com/distribution/456",
            rc: "rc1",
            rcNumber: "1",
            platform: "linux",
            architecture: "x64",
            distribution: "tar",
            testReportManifestYml: "path/to/testReportManifest.yml",
            jobName: "test-job"
        ]

        def result = script.call(args)
        
        assert result == null
    }

    def normalizeString(String str) {
        return str.replaceAll(/\s+/, " ").trim()
    }
 }