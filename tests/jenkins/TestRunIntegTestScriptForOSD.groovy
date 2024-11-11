/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat

class TestRunIntegTestScriptForOSD extends BuildPipelineTest {

    @Test
    public void TestRunProcessScript() {
        this.registerLibTester(new RunIntegTestScriptForOSDLibTester(
                'reportsDashboard',
                '',
                'false',
                'dummy/path/opensearch',
                'test-bucket',
                'dummy/artifact/path',
                'x64',
                'tests/data/opensearch-1.3.0-build.yml',
                'tests/data/opensearch-1.3.0-test.yml'
        )
        )
        super.setUp()
        super.testPipeline("tests/jenkins/jobs/RunIntegTestScriptForOSD_Jenkinsfile")
        runScript("tests/jenkins/jobs/RunIntegTestScriptForOSD_Jenkinsfile")
        assertThat(getShellCommands('sh', 'test.sh'), hasItems('env PATH=$PATH JAVA_HOME=/opt/java/openjdk-11 ./test.sh integ-test manifests/tests/data/opensearch-1.3.0-test.yml --component reportsDashboard  --test-run-id null --paths opensearch=/home/user/x64 --base-path null/null/1.3.0/29/linux/x64/tar '))
    }

    @Test
    public void TestRunProcessScriptWithoutCiGroup() {
        this.registerLibTester(new RunIntegTestScriptForOSDLibTester(
                'OpenSearch-Dashboards',
                '1',
                'false',
                'dummy/path/opensearch',
                'test-bucket',
                'dummy/artifact/path',
                'x64',
                'tests/data/opensearch-1.3.0-build.yml',
                'tests/data/opensearch-1.3.0-test.yml'
        )
        )
        super.setUp()
        super.testPipeline("tests/jenkins/jobs/RunIntegTestScriptForOSDCiGroup_Jenkinsfile")
        runScript("tests/jenkins/jobs/RunIntegTestScriptForOSDCiGroup_Jenkinsfile")
        assertThat(getShellCommands('sh','test.sh'), hasItems('env PATH=$PATH JAVA_HOME=/opt/java/openjdk-11 ./test.sh integ-test manifests/tests/data/opensearch-1.3.0-test.yml --component OpenSearch-Dashboards --ci-group 1 --test-run-id null --paths opensearch=/home/user/x64 --base-path null/null/1.3.0/29/linux/x64/tar '))
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
