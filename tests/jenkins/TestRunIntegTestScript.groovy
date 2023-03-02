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
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat


class TestRunIntegTestScript extends BuildPipelineTest {

    @Test
    public void TestRunIntegTestScript() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
            'dummy_job',
            'OpenSearch',
            'tests/data/opensearch-1.3.0-build.yml',
            'tests/data/opensearch-1.3.0-test.yml',
            '',
            '',
            )
        )

        super.testPipeline("tests/jenkins/jobs/RunIntegTestScript_Jenkinsfile")
    }

    @Test
    public void TestRunIntegTestScript_OpenSearch_Dashboards() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
            'dummy_job',
            'functionalTestDashboards',
            'tests/data/opensearch-dashboards-1.2.0-build.yml',
            'tests/data/opensearch-dashboards-1.2.0-test.yml',
            '',
            '',
            )
        )

        super.testPipeline("tests/jenkins/jobs/RunIntegTestScript_OpenSearch_Dashboards_Jenkinsfile")
    }

    @Test
    public void TestRunIntegTestScript_LocalPath() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
            'dummy_job',
            'OpenSearch',
            'tests/data/opensearch-1.3.0-build.yml',
            'tests/data/opensearch-1.3.0-test.yml',
            'tests/jenkins/artifacts/tar',
            '',
            )
        )

        super.testPipeline("tests/jenkins/jobs/RunIntegTestScript_LocalPath_Jenkinsfile")
    }

    @Test
    public void TestRunIntegTestScript_LocalPath_Switch_Non_Root() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
            'dummy_job',
            'OpenSearch',
            'tests/data/opensearch-1.3.0-build.yml',
            'tests/data/opensearch-1.3.0-test.yml',
            'tests/jenkins/artifacts/tar',
            'true',
            )
        )

        super.testPipeline("tests/jenkins/jobs/RunIntegTestScript_LocalPath_Switch_Non_Root_Jenkinsfile")
    }

    @Test
    void 'IntegTest LocalPath SwitchNonRoot=false'() {
        runScript("tests/jenkins/jobs/RunIntegTestScript_LocalPath_Jenkinsfile")
        assertThat(getShellCommands('sh', 'test.sh'), hasItems('  ./test.sh integ-test tests/data/opensearch-1.3.0-test.yml --component OpenSearch --test-run-id null --paths opensearch=tests/jenkins/artifacts/tar '))

    }

    @Test
    void 'IntegTest LocalPath SwitchNonRoot=true'() {
        runScript("tests/jenkins/jobs/RunIntegTestScript_LocalPath_Switch_Non_Root_Jenkinsfile")
        assertThat(getShellCommands('sh', 'test.sh'), hasItems('su - `id -un 1000` -c \" cd bbb\nccc &&  ./test.sh integ-test tests/data/opensearch-1.3.0-test.yml --component OpenSearch --test-run-id null --paths opensearch=tests/jenkins/artifacts/tar \"'))

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
