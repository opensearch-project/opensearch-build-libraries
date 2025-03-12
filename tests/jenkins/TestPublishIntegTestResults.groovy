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
    void testIndexTestFailuresData() {
        def indexName = 'opensearch-integration-test-failures-test-index'
        def testRecordsFile = 'test-failures.json'

        def script = loadScript('vars/publishIntegTestResults.groovy')

        def calledCommands = new ArrayList()
        script.metaClass.sh = { String command ->
            calledCommands << command
            if (command.contains("curl -I")) {
                return "HTTP/1.1 200 OK"
            } else if (command.contains("curl -s -XPUT") && command.contains(indexName)) {
                return '{"acknowledged":true}'
            } else if (command.contains("curl -XPOST") && command.contains(indexName)) {
                return '{"took":10, "errors":false}'
            } else {
                throw new IllegalArgumentException("Unexpected command: $command")
            }
        }

        script.indexTestFailuresData(indexName, testRecordsFile)

        def expectedCommandBlock = '''set +e
    set +x
    echo "INDEX NAME IS opensearch-integration-test-failures-test-index"
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
    curl -I "METRICS_HOST_URL/opensearch-integration-test-failures-test-index" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" | grep -E "HTTP\\/[0-9]+(\\.[0-9]+)? 200"
    if [ $? -eq 0 ]; then
        echo "Index already exists. Indexing Results"
    else
        echo "Index does not exist. Creating..."
        create_index_response=$(curl -s -XPUT "METRICS_HOST_URL/opensearch-integration-test-failures-test-index" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" -H 'Content-Type: application/json' -d "${INDEX_MAPPING}")
        if [[ $create_index_response == *'"acknowledged":true'* ]]; then
            echo "Index created successfully."
            echo "Updating alias..."
            update_alias_response=$(curl -s -XPOST "METRICS_HOST_URL/_aliases" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" -H "Content-Type: application/json" -d '{ 
                "actions": [
                    {
                        "add": {
                        "index": "opensearch-integration-test-failures-test-index",
                        "alias": "opensearch-integration-test-failures"
                        }
                    }
                ]
            }')
            if [[ $update_alias_response == *'"acknowledged":true'* ]]; then
                echo "Alias updated successfully."
            else
                echo "Failed to update alias. Error message: $update_alias_response"
            fi
        else
            echo "Failed to create index. Error message: $create_index_response"
            exit 1
        fi
    fi
    if [ -s test-failures.json ]; then
        echo "File Exists, indexing failed tests."
        curl -XPOST "METRICS_HOST_URL/opensearch-integration-test-failures-test-index/_bulk" --aws-sigv4 "aws:amz:us-east-1:es" --user "null:null" -H "x-amz-security-token:null" -H "Content-Type: application/x-ndjson" --data-binary "@test-failures.json"
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
            'component1', 'componentRepo', 'https://componentRepoUrl', '1.0', 'alpha1', 123,
            'http://example.com/build/123', 456, 'http://example.com/distribution/456',
            System.currentTimeMillis(), 'rc1', 1, 'linux', 'x64', 'tar', 'test-category',
            'failed', 'http://example.com/test-report.yml', 'pass', 'yml1', ['cluster_stdout1'], ['cluster_stderr1'], ['test_stdout1'], ['test_stderr1'],
            'fail', 'yml2', ['cluster_stdout2'], ['cluster_stderr2'], ['test_stdout2'], ['test_stderr2']
        )

        def parsedResult = new JsonSlurper().parseText(result)
        def expectedJson = [
            component: 'component1',
            component_repo: 'componentRepo',
            component_repo_url: 'https://componentRepoUrl',
            version: '1.0',
            qualifier: 'alpha1',
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
            with_security_cluster_stdout: ['cluster_stdout1'],
            with_security_cluster_stderr: ['cluster_stderr1'],
            with_security_test_stdout: ['test_stdout1'],
            with_security_test_stderr: ['test_stderr1'],
            without_security: 'fail',
            without_security_build_yml: 'yml2',
            without_security_cluster_stdout: ['cluster_stdout2'],
            without_security_cluster_stderr: ['cluster_stderr2'],
            without_security_test_stdout: ['test_stdout2'],
            without_security_test_stderr: ['test_stderr2']
        ]

        // Remove the dynamic field for comparison
        parsedResult.remove('build_start_time')
        assert parsedResult == expectedJson
    }

    @Test
    void testGenerateFailedTestJson() {
        def script = loadScript('vars/publishIntegTestResults.groovy')

        def result = script.generateFailedTestJson(
            'component1', 'componentRepo', 'https://componentRepoUrl', '1.0', 'None', 123,
            'http://example.com/build/123', 456, 'http://example.com/distribution/456',
            System.currentTimeMillis(), 'rc1', 1, 'linux', 'x64', 'tar', 'test_category',
            'test_type', 'test_class', 'test_name'
        )

        def parsedResult = new JsonSlurper().parseText(result)
        def expectedJson = [
            component: 'component1',
            component_repo: 'componentRepo',
            component_repo_url: 'https://componentRepoUrl',
            version: '1.0',
            qualifier: 'None',
            integ_test_build_number: 123,
            integ_test_build_url: 'http://example.com/build/123',
            distribution_build_number: 456,
            distribution_build_url: 'http://example.com/distribution/456',
            rc: 'rc1',
            rc_number: 1,
            platform: 'linux',
            architecture: 'x64',
            distribution: 'tar',
            component_category: 'test_category',
            test_type: 'test_type',
            test_class: 'test_class',
            test_name: 'test_name'
        ]

        // Remove the dynamic field for comparison
        parsedResult.remove('build_start_time')
        assert parsedResult == expectedJson
    }

    @Test
    void testProcessFailedTestsWithEmptyList_withInputs() {
        def failedTests = []
        def componentName = "MyComponent"
        def componentRepo = "my-repo"
        def componentRepoUrl = "https://example.com/my-repo"
        def version = "1.0.0"
        def qualifier = "alpha1"
        def integTestBuildNumber = 123
        def integTestBuildUrl = "https://example.com/builds/123"
        def distributionBuildNumber = 456
        def distributionBuildUrl = "https://example.com/builds/456"
        def buildStartTime = "2023-10-17T12:00:00Z"
        def rc = "RC1"
        def rcNumber = 1
        def platform = "Linux"
        def architecture = "x86_64"
        def distribution = "Ubuntu 22.04"
        def componentCategory = "Backend"
        def securityType = "Web Application"
        def testFailuresindexName = "test-failures-index"
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.processFailedTests(failedTests, componentName, componentRepo, componentRepoUrl, version, qualifier, integTestBuildNumber, integTestBuildUrl, distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber, platform, architecture, distribution, componentCategory, securityType, testFailuresindexName)
        assert result == ""
    }

    @Test
    void testProcessFailedTestsWithEmptyList() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.processFailedTests([], 'component1', 'componentRepo', 'https://componentRepoUrl', '1.0', 'None',
            123, 'http://example.com/build/123', 456, 'http://example.com/distribution/456',
            System.currentTimeMillis(), 'rc1', 1, 'linux', 'x64', 'tar', 'test_category',
            'test_type', 'test_failures_index')
        assert result == ""
    }

    @Test
    void testProcessFailedTestsWithTestResultNotAvailable() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.processFailedTests(['Test Result Not Available'], 'component1', 'componentRepo',
            'https://componentRepoUrl', '1.0', 'None', 123, 'http://example.com/build/123', 456,
            'http://example.com/distribution/456', System.currentTimeMillis(), 'rc1', 1,
            'linux', 'x64', 'tar', 'test_category', 'test_type', 'test_failures_index')
        def expectedJson = [
            [
                index: [
                    _index: 'test_failures_index'
                ]
            ],
            [
                component: 'component1',
                component_repo: 'componentRepo',
                component_repo_url: 'https://componentRepoUrl',
                version: '1.0',
                qualifier: 'None',
                integ_test_build_number: 123,
                integ_test_build_url: 'http://example.com/build/123',
                distribution_build_number: 456,
                distribution_build_url: 'http://example.com/distribution/456',
                rc: 'rc1',
                rc_number: 1,
                platform: 'linux',
                architecture: 'x64',
                distribution: 'tar',
                component_category: 'test_category',
                test_type: 'test_type',
                test_class: 'Result Not Available',
                test_name: 'Result Not Available'
            ]
        ]
        def parsedResult = result.trim().split('\n').collect { new JsonSlurper().parseText(it) }
        parsedResult.each { json ->
            json.remove('build_start_time')
        }
        assert parsedResult == expectedJson
    }

    @Test
    void testProcessFailedTestsWithTestResultFilesListNotAvailable() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.processFailedTests(['Test Result Files List Not Available'], 'component1', 'componentRepo',
            'https://componentRepoUrl', '1.0', 'alpha1', 123, 'http://example.com/build/123', 456,
            'http://example.com/distribution/456', System.currentTimeMillis(), 'rc1', 1,
            'linux', 'x64', 'tar', 'test_category', 'test_type', 'test_failures_index')
        def expectedJson = [
            [
                index: [
                    _index: 'test_failures_index'
                ]
            ],
            [
                component: 'component1',
                component_repo: 'componentRepo',
                component_repo_url: 'https://componentRepoUrl',
                version: '1.0',
                qualifier: 'alpha1',
                integ_test_build_number: 123,
                integ_test_build_url: 'http://example.com/build/123',
                distribution_build_number: 456,
                distribution_build_url: 'http://example.com/distribution/456',
                rc: 'rc1',
                rc_number: 1,
                platform: 'linux',
                architecture: 'x64',
                distribution: 'tar',
                component_category: 'test_category',
                test_type: 'test_type',
                test_class: 'Report Not Available',
                test_name: 'Report Not Available'
            ]
        ]
        def parsedResult = result.trim().split('\n').collect { new JsonSlurper().parseText(it) }
        parsedResult.each { json ->
            json.remove('build_start_time')
        }
        assert parsedResult == expectedJson
    }


    @Test
    void testProcessFailedTestsWithNoFailedTest() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.processFailedTests(['No Failed Test'], 'component1', 'componentRepo',
            'https://componentRepoUrl', '1.0', 'None', 123, 'http://example.com/build/123', 456,
            'http://example.com/distribution/456', System.currentTimeMillis(), 'rc1', 1,
            'linux', 'x64', 'tar', 'test_category', 'test_type', 'test_failures_index')

       assert result == ""
    }

    @Test
    void testProcessFailedTestsSampleOpenSearchFailures() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.processFailedTests(['org.opensearch.indexmanagement.rollup.runner.RollupRunnerIT#test search max buckets breaker'], 'os_component',
            'os_componentRepo', 'https://os_componentRepoUrl', '1.0', 'None', 123, 'http://example.com/os-build/123', 456,
            'http://example.com/os-distribution/456', System.currentTimeMillis(), 'rc1', 1,
            'linux', 'x64', 'tar', 'OpenSearch', 'test_type', 'test_failures_index')
        def expectedJson = [
            [
                index: [
                    _index: 'test_failures_index'
                ]
            ],
            [
                component: 'os_component',
                component_repo: 'os_componentRepo',
                component_repo_url: 'https://os_componentRepoUrl',
                version: '1.0',
                qualifier: 'None',
                integ_test_build_number: 123,
                integ_test_build_url: 'http://example.com/os-build/123',
                distribution_build_number: 456,
                distribution_build_url: 'http://example.com/os-distribution/456',
                rc: 'rc1',
                rc_number: 1,
                platform: 'linux',
                architecture: 'x64',
                distribution: 'tar',
                component_category: 'OpenSearch',
                test_type: 'test_type',
                test_class: 'org.opensearch.indexmanagement.rollup.runner.RollupRunnerIT',
                test_name: 'test search max buckets breaker'
            ]

        ]
        def parsedResult = result.trim().split('\n').collect { new JsonSlurper().parseText(it) }
        parsedResult.each { json ->
            json.remove('build_start_time')
        }
        assert parsedResult == expectedJson
    }

    @Test
    void testProcessFailedTestsSampleOpenSearchDashboardsFailures() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def result = script.processFailedTests(['integration/plugins/security-analytics-dashboards-plugin/4_findings.spec#Findings \"before all\" hook for \"displays findings based on recently ingested data\"'], 'osd_component',
            'osd_componentRepo', 'https://osd_componentRepoUrl', '1.0', 'None', 123, 'http://example.com/osd-build/123', 456,
            'http://example.com/osd-distribution/456', System.currentTimeMillis(), 'rc1', 1,
            'linux', 'x64', 'tar', 'OpenSearch Dashboards', 'test_type', 'test_failures_index')
        def expectedJson = [
            [
                index: [
                    _index: 'test_failures_index'
                ]
            ],
            [
                component: 'osd_component',
                component_repo: 'osd_componentRepo',
                component_repo_url: 'https://osd_componentRepoUrl',
                version: '1.0',
                qualifier: 'None',
                integ_test_build_number: 123,
                integ_test_build_url: 'http://example.com/osd-build/123',
                distribution_build_number: 456,
                distribution_build_url: 'http://example.com/osd-distribution/456',
                rc: 'rc1',
                rc_number: 1,
                platform: 'linux',
                architecture: 'x64',
                distribution: 'tar',
                component_category: 'OpenSearch Dashboards',
                test_type: 'test_type',
                test_class: 'integration/plugins/security-analytics-dashboards-plugin/4_findings.spec',
                test_name: 'Findings \"before all\" hook for \"displays findings based on recently ingested data\"'
            ]

        ]
        def parsedResult = result.trim().split('\n').collect { new JsonSlurper().parseText(it) }
        parsedResult.each { json ->
            json.remove('build_start_time')
        }
        assert parsedResult == expectedJson
    }


    @Test
    void testComponentResultWithSecurityFail() {
        def withSecurity = 'fail'
        def withoutSecurity = 'pass'
        def componentResult = (withSecurity == 'fail' || withoutSecurity == 'fail' || withSecurity == 'Not Available' || withoutSecurity == 'Not Available') ? 'failed' : 'passed'
        assert componentResult == 'failed'
    }

    @Test
    void testComponentResultWithoutSecurityFail() {
        def withSecurity = 'pass'
        def withoutSecurity = 'fail'
        def componentResult = (withSecurity == 'fail' || withoutSecurity == 'fail' || withSecurity == 'Not Available' || withoutSecurity == 'Not Available') ? 'failed' : 'passed'

        assert componentResult == 'failed'
    }

    @Test
    void testComponentResultWithSecurityNotAvailable() {
        def withSecurity = 'Not Available'
        def withoutSecurity = 'pass'
        def componentResult = (withSecurity == 'fail' || withoutSecurity == 'fail' || withSecurity == 'Not Available' || withoutSecurity == 'Not Available') ? 'failed' : 'passed'

        assert componentResult == 'failed'
    }

    @Test
    void testComponentResultWithoutSecurityNotAvailable() {
        def withSecurity = 'pass'
        def withoutSecurity = 'Not Available'
        def componentResult = (withSecurity == 'fail' || withoutSecurity == 'fail' || withSecurity == 'Not Available' || withoutSecurity == 'Not Available') ? 'failed' : 'passed'

        assert componentResult == 'failed'
    }

    @Test
    void testComponentResultBothPass() {
        def withSecurity = 'pass'
        def withoutSecurity = 'pass'
        def componentResult = (withSecurity == 'fail' || withoutSecurity == 'fail' || withSecurity == 'Not Available' || withoutSecurity == 'Not Available') ? 'failed' : 'passed'

        assert componentResult == 'passed'
    }


    @Test
    void testCallWithMissingArgs() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
        def args = [
            distributionBuildUrl: "http://example.com/distribution/456",
            testReportManifestYml: "path/to/testReportManifest.yml",
        ]

        def result = script.call(args)

        assert result == null
    }

    @Test
    void testCallWithEmptyArgs() {
        def script = loadScript('vars/publishIntegTestResults.groovy')
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
