/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class TestPublishSmokeTestResults extends BuildPipelineTest {

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
        binding.setVariable('readFile', { filePath -> 'components:\n- name: component1\n  configs:\n    - name: GET___cat_plugins\n      status: PASS\n    - name: POST___bulk\n      status: FAIL' })
        binding.setVariable('writeFile', { params -> println params.text })
        binding.setVariable('withCredentials', { creds, closure -> closure() })
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('curl', { params -> println params })
    }

    @Test
    void testIndexSmokeTestData() {
        def indexName = 'test-index'
        def testRecordsFile = 'test-records.ndjson'

        def script = loadScript('vars/publishSmokeTestResults.groovy')

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

        script.indexSmokeTestData(indexName, testRecordsFile)

        def expectedCommandBlock = '''set +e
        set +x
        echo "INDEX NAME IS test-index"
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
        curl -I "METRICS_HOST_URL/test-index" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" | grep -E "HTTP\\/[0-9]+(\\.[0-9]+)? 200"
        if [ $? -eq 0 ]; then
            echo "Index already exists. Indexing Results"
        else
            echo "Index does not exist. Creating..."
            create_index_response=$(curl -s -XPUT "METRICS_HOST_URL/test-index" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" -H 'Content-Type: application/json' -d "${INDEX_MAPPING}")
            if [[ $create_index_response == *'"acknowledged":true'* ]]; then
                echo "Index created successfully."
                echo "Updating alias..."
                update_alias_response=\$(curl -s -XPOST "METRICS_HOST_URL/_aliases" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" -H "Content-Type: application/json" -d '{
                    "actions": [
                        {
                            "add": {
                            "index": "test-index",
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
        def script = loadScript('vars/publishSmokeTestResults.groovy')
        def result = script.generateJson(
            'component1', 'componentRepo', 'https://componentRepoUrl', '1.0', 'alpha1', 123,
            'http://example.com/build/123', 456, 'http://example.com/distribution/456',
            System.currentTimeMillis(), 'rc1', 1, 'linux', 'x64', 'tar', 'test-category',
            'PASS', 'http://example.com/test-report.yml', 'GET__test_api', 'test-spec-yml1', ['cluster_stdout1'], ['cluster_stderr1'], ['test_stdout1'], ['test_stderr1']
        )

        def parsedResult = new JsonSlurper().parseText(result)
        def expectedJson = [
                component: 'component1',
                component_repo: 'componentRepo',
                component_repo_url: 'https://componentRepoUrl',
                version: '1.0',
                qualifier: 'alpha1',
                smoke_test_build_number: 123,
                smoke_test_build_url: 'http://example.com/build/123',
                distribution_build_number: 456,
                distribution_build_url: 'http://example.com/distribution/456',
                // Ignore build_start_time for comparison
                rc: 'rc1',
                rc_number: 1,
                platform: 'linux',
                architecture: 'x64',
                distribution: 'tar',
                component_category: 'test-category',
                test_report_manifest_yml: 'http://example.com/test-report.yml',
                component_test_spec_name: 'GET__test_api',
                component_test_spec_yml: 'test-spec-yml1',
                component_test_spec_result: 'PASS',
                component_test_spec_cluster_stdout: ['cluster_stdout1'],
                component_test_spec_cluster_stderr: ['cluster_stderr1'],
                "component_test_spec_test_stdout": ['test_stdout1'],
                component_test_spec_test_stderr: ['test_stderr1'],
        ]

        // Remove the dynamic field for comparison
        parsedResult.remove('build_start_time')
        assert parsedResult == expectedJson
    }

    @Test
    void testCallWithMissingArgs() {
        def script = loadScript('vars/publishSmokeTestResults.groovy')
        def args = [
            distributionBuildUrl: "http://example.com/distribution/456",
            testReportManifestYml: "path/to/testReportManifest.yml",
        ]

        def result = script.call(args)

        assert result == null
    }

    @Test
    void testCallWithEmptyArgs() {
        def script = loadScript('vars/publishSmokeTestResults.groovy')
        def args = [
            distributionBuildUrl: "http://example.com/distribution/456",
            testReportManifestYml: "path/to/testReportManifest.yml",
            jobName: ""
        ]

        def result = script.call(args)

        assert result == null
    }

    def normalizeString(String str) {
        return str.replaceAll(/\s+/, " ").trim()
    }
 }
