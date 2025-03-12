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
import groovy.json.JsonOutput

class TestPublishDistributionBuildResults extends BuildPipelineTest {

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
        binding.setVariable('readFile', { filePath -> 'components:\n- name: component1\n  repository: repo1\n  ref: ref1' })
        binding.setVariable('writeFile', { params -> println params.text })
        binding.setVariable('withCredentials', { creds, closure -> closure() })
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('curl', { params -> println params })
    }

    @Test
    void testIndexBuildData() {
        def indexName = 'test-index'
        def testRecordsFile = 'test-records.ndjson'

        def script = loadScript('vars/publishDistributionBuildResults.groovy')

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
                    "component_ref": {
                        "type": "keyword"
                    },
                    "version": {
                        "type": "keyword"
                    },
                    "qualifier": {
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
        def script = loadScript('vars/publishDistributionBuildResults.groovy')
        def result = script.generateJson(
            'component1', 'componentRepo', 'https://componentRepoUrl', 'ref1', '1.0', 'rc1', 123,
            'http://example.com/build/123', System.currentTimeMillis(), 'rc1', 1, 'test-category', 'failed'
        )

        def parsedResult = new JsonSlurper().parseText(result)
        def expectedJson = [
            component: 'component1',
            component_repo: 'componentRepo',
            component_repo_url: 'https://componentRepoUrl',
            component_ref: 'ref1',
            version: '1.0',
            qualifier: 'rc1',
            distribution_build_number: 123,
            distribution_build_url: 'http://example.com/build/123',
            // Ignore build_start_time for comparison
            rc: 'rc1',
            rc_number: 1,
            component_category: 'test-category',
            component_build_result: 'failed'
        ]

        // Remove the dynamic field for comparison
        parsedResult.remove('build_start_time')
        assert parsedResult == expectedJson
    }

    def normalizeString(String str) {
        return str.replaceAll(/\s+/, " ").trim()
    }

    @Test
    void testGenerateAndAppendJson() {
        def script = loadScript('vars/publishDistributionBuildResults.groovy')
        // Test valid parameters
        def indexName = "test-index"
        def component = "componentA"
        def componentRepo = "componentRepo"
        def componentRepoUrl = "https://componentRepoUrl"
        def componentRef = "refA"
        def version = "1.0.0"
        def qualifier = "None"
        def distributionBuildNumber = "123"
        def distributionBuildUrl = "http://example.com/build/123"
        def buildStartTime = "2024-07-19T00:00:00Z"
        def rc = true
        def rcNumber = "RC1"
        def componentCategory = "categoryA"
        def status = "success"

        def result = script.generateAndAppendJson(component, componentRepo, componentRepoUrl, componentRef, version, qualifier,distributionBuildNumber, distributionBuildUrl, buildStartTime, rc, rcNumber, componentCategory, status)
        def expectedJson = JsonOutput.toJson([
            component: component,
            component_repo: componentRepo,
            component_repo_url: componentRepoUrl,
            component_ref: componentRef,
            version: version,
            qualifier: qualifier,
            distribution_build_number: distributionBuildNumber,
            distribution_build_url: distributionBuildUrl,
            build_start_time: buildStartTime,
            rc: rc,
            rc_number: rcNumber,
            component_category: componentCategory,
            component_build_result: status
        ])
        assert result == expectedJson

        result = script.generateAndAppendJson(null, null, null, null, null, null, null, null, null, null, null, null, null)
        expectedJson = JsonOutput.toJson([
            component: null,
            component_repo: null,
            component_repo_url: null,
            component_ref: null,
            version: null,
            qualifier: null,
            distribution_build_number: null,
            distribution_build_url: null,
            build_start_time: null,
            rc: null,
            rc_number: null,
            component_category: null,
            component_build_result: null
        ])
        assert result == expectedJson
    }

    @Test
    void testExtractComponentsForFailureMessages() {
        List<String> failureMessages = [
            "Error building componentA, caused by ...",
            "Error building componentB due to ...",
            "Error building componentA because ..."
        ]
        List<String> expectedComponents = ["componentA", "componentB"]
        def script = loadScript('vars/publishDistributionBuildResults.groovy')
        List<String> result = script.extractComponents(failureMessages, /(?<=\bError building\s).*/, 0)
        assert result == expectedComponents
    }

    @Test
    void testExtractComponentsForPassMessages() {
        List<String> passMessages = [
            "Successfully built componentA",
            "Successfully built componentB",
            "Successfully built componentC"
        ]
        List<String> expectedComponents = ["componentA", "componentB", "componentC"]
        def script = loadScript('vars/publishDistributionBuildResults.groovy')
        List<String> result = script.extractComponents(passMessages, /(?<=\bSuccessfully built\s).*/, 0)
        assert result == expectedComponents
    }

    @Test
    void testExtractComponentsWithDuplicates() {
        List<String> messages = [
            "Successfully built componentA",
            "Successfully built componentA",
            "Successfully built componentB"
        ]
        List<String> expectedComponents = ["componentA", "componentB"]
        def script = loadScript('vars/publishDistributionBuildResults.groovy')
        List<String> result = script.extractComponents(messages, /(?<=\bSuccessfully built\s).*/, 0)
        assert result == expectedComponents
    }
}
