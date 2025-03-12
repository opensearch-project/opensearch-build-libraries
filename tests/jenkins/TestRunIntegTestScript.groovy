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
        super.setUp()
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
        super.setUp()
        runScript("tests/jenkins/jobs/RunIntegTestScript_OpenSearch_Dashboards_Jenkinsfile")
        assertThat(getShellCommands('sh', 'test.sh'), hasItems('env PATH=$PATH  ./test.sh integ-test tests/data/opensearch-dashboards-1.2.0-test.yml --component functionalTestDashboards --ci-group 1 --test-run-id 987 --paths opensearch=https://ci.opensearch.org/ci/dbc/distribution-build-opensearch/1.2.0/latest/linux/x64/tar opensearch-dashboards=https://ci.opensearch.org/ci/dbc/dummy_job/1.2.0/215/linux/x64/tar --base-path https://dummy_link/dummy_integ_test/1.2.0/215/linux/x64/tar '))
    }

    @Test
    public void TestRunIntegTestScript_LocalPath() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
                'dummy_job',
                'OpenSearch-Dashboards',
                'tests/data/opensearch-dashboards-1.2.0-build.yml',
                'tests/data/opensearch-dashboards-1.2.0-test.yml',
                'tests/jenkins/artifacts/tar',
                '',
            )
        )
        super.setUp()
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
        super.setUp()
        super.testPipeline("tests/jenkins/jobs/RunIntegTestScript_LocalPath_Switch_Non_Root_Jenkinsfile")
    }

    @Test
    void 'IntegTest Windows Zip'() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
                'dummy_job',
                'OpenSearch',
                'tests/data/opensearch-2.8.0-build-windows.yml',
                'tests/data/opensearch-2.8.0-test.yml',
                '',
                '',
            )
        )
        super.setUp()
        runScript("tests/jenkins/jobs/RunIntegTestScript_Windows_Jenkinsfile")
        assertThat(getShellCommands('sh', 'test.sh'), hasItems('env PATH=$PATH  ./test.sh integ-test tests/data/opensearch-2.8.0-test.yml --component OpenSearch  --test-run-id 987 --paths opensearch=https://ci.opensearch.org/ci/dbc/dummy_job/2.8.0/7923/windows/x64/zip --base-path https://dummy_link/dummy_integ_test/2.8.0/7923/windows/x64/zip '))

    }

    @Test
    void 'IntegTest LocalPath SwitchNonRoot=false'() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
                'dummy_job',
                'OpenSearch-Dashboards',
                'tests/data/opensearch-dashboards-1.2.0-build.yml',
                'tests/data/opensearch-dashboards-1.2.0-test.yml',
                'tests/jenkins/artifacts/tar',
                'false',
            )
        )
        super.setUp()
        runScript("tests/jenkins/jobs/RunIntegTestScript_LocalPath_Jenkinsfile")
        assertThat(getShellCommands('sh', 'test.sh'), hasItems('env PATH=$PATH  ./test.sh integ-test tests/data/opensearch-dashboards-1.2.0-test.yml --component OpenSearch-Dashboards  --test-run-id 987 --paths opensearch=tests/jenkins/artifacts/tar opensearch-dashboards=tests/jenkins/artifacts/tar --base-path https://dummy_link/dummy_integ_test/1.2.0/215/linux/x64/tar '))

    }

    @Test
    void 'IntegTest LocalPath SwitchNonRoot=true with JAVA_HOME'() {
        this.registerLibTester(new RunIntegTestScriptLibTester(
                'dummy_job',
                'OpenSearch',
                'tests/data/opensearch-1.3.0-build.yml',
                'tests/data/opensearch-1.3.0-test.yml',
                'tests/jenkins/artifacts/tar',
                'true',
            )
        )
        super.setUp()
        runScript("tests/jenkins/jobs/RunIntegTestScript_LocalPath_Switch_Non_Root_Jenkinsfile")
        assertThat(getShellCommands('sh', 'test.sh'), hasItems('su `id -un 1000` -c \"env PATH=$PATH JAVA_HOME=/opt/java/openjdk-11 ./test.sh integ-test tests/data/opensearch-1.3.0-test.yml --component OpenSearch  --test-run-id 987 --paths opensearch=tests/jenkins/artifacts/tar --base-path https://dummy_link/dummy_integ_test/1.3.0/29/linux/x64/tar \"'))

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
