/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */


import jenkins.tests.BuildPipelineTest
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat

class TestRunSmokeTestScript extends BuildPipelineTest {

    @Test
    public void TestRunSmokeTestScript() {
        this.registerLibTester(new RunSmokeTestScriptLibTester(
                'dummy_job',
                'tests/data/opensearch-2.18.0-build.yml',
                'tests/data/opensearch-2.18.0-test.yml',
                '1234',
                'false'
            )
        )

        binding.setVariable('env', ['BUILD_NUMBER': '9876'])
        super.testPipeline("tests/jenkins/jobs/RunSmokeTestScript_Jenkinsfile")
        assertThat(getShellCommands('sh', './test.sh'), hasItems(' ./test.sh smoke-test tests/data/opensearch-2.18.0-test.yml --test-run-id 9876 --paths opensearch=https://ci.opensearch.org/ci/dbc/dummy_job/2.18.0/1234/linux/x64/tar '))
    }

    @Test
    public void TestRunSmokeTestScript_Switch_Non_Root() {
        this.registerLibTester(new RunSmokeTestScriptLibTester(
                'dummy_job',
                'tests/data/opensearch-2.18.0-build.yml',
                'tests/data/opensearch-2.18.0-test.yml',
                '2345',
                'true'
            )
        )

        binding.setVariable('env', ['BUILD_NUMBER': '8765'])
        super.testPipeline("tests/jenkins/jobs/RunSmokeTestScript_Switch_Non_Root_Jenkinsfile")
        assertThat(getShellCommands('sh', './test.sh'), hasItems('su `id -un 1000` -c \" ./test.sh smoke-test tests/data/opensearch-2.18.0-test.yml --test-run-id 8765 --paths opensearch=https://ci.opensearch.org/ci/dbc/dummy_job/2.18.0/2345/linux/x64/tar \"'))
    }

    def getShellCommands(methodName, searchString) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == methodName
        }.collect { call ->
            callArgsToString(call)
        }.findAll { command ->
            command.contains(searchString)
        }
        return shCommands
    }
}
