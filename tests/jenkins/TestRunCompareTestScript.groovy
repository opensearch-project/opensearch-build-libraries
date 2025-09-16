/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import jenkins.tests.BuildPipelineTest
import org.junit.Before
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestRunCompareTestScript extends BuildPipelineTest {
    @Before
    void setUp() {
        this.registerLibTester(new RunCompareTestScriptLibTester(
                'compare',
                '12345',
                '54321',
                'csv',
                'left',
                '/results/final_results',
                'true',
                'compare-tester',
                'tests/data/opensearch-1.3.0-bundle.yml'
        ))
        super.setUp()
        helper.registerAllowedMethod("withSecrets", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        binding.setVariable('ARTIFACT_BUCKET_NAME', "ARTIFACT_BUCKET_NAME")
        binding.setVariable('AWS_ACCOUNT_PUBLIC', "AWS_ACCOUNT_PUBLIC")
        binding.setVariable('DATASTORE_USER', "DATASTORE_USER")
        binding.setVariable('DATASTORE_PASSWORD', "DATASTORE_PASSWORD")
        binding.setVariable('DATASTORE_ENDPOINT', "DATASTORE_ENDPOINT")
        binding.setVariable('PERF_TEST_ACCOUNT_ID', "PERF_TEST_ACCOUNT_ID")
    }

    @Test
    public void testRunCompareTestScript_PipelineSingleNode() {
        super.testPipeline("tests/jenkins/jobs/CompareTest_Jenkinsfile")
    }

    @Test 
    void testRunCompareTestScript_verifyScriptExecutionSingleNode() {
        runScript("tests/jenkins/jobs/CompareTest_Jenkinsfile")

        def testScriptCommands = getCommandExecutions('sh', './test.sh').findAll {
            shCommand -> shCommand.contains('./test.sh')
        }

        assertThat(testScriptCommands.size(), equalTo(2))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test compare 12345 54321 --benchmark-config /tmp/workspace/benchmark.ini --suffix compare-tester"
        ))
        assertThat(testScriptCommands, hasItem(
                "set +x && ./test.sh benchmark-test compare 12345 54321 --benchmark-config /tmp/workspace/benchmark.ini --suffix compare-tester --results-format=csv --results-numbers-align=left --results-file=/results/final_results --show-in-results=true"
        ))
    }

    def getCommandExecutions(methodName, command) {
        def shCommands = helper.callStack.findAll {
            call ->
                call.methodName == methodName
        }.
                collect {
                    call ->
                        callArgsToString(call)
                }.findAll {
            shCommand ->
                shCommand.contains(command)
        }

        return shCommands
    }

}
