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


class TestCreateUploadTestReportManifest extends BuildPipelineTest {

    @Test
    public void TestCreateUploadTestReportManifest() {
        this.registerLibTester(new CreateUploadTestReportManifestLibTester(
                'tests/data/opensearch-1.3.0-test.yml',
                'tests/data/opensearch-1.3.0-build.yml',
                'tests/data/opensearch-dashboards-build-1.3.0.yml',
                '1234',
                'integ-test',
                '',
            )
        )
        super.setUp()
        super.testPipeline("tests/jenkins/jobs/CreateUploadTestReportManifest_Jenkinsfile")
        assertThat(getShellCommands('sh', 'report.sh'), hasItems('./report.sh tests/data/opensearch-1.3.0-test.yml --artifact-paths opensearch=https://ci.opensearch.org/ci/dbc/distribution-build-opensearch/1.3.0/29/linux/x64/tar --test-run-id 1234 --test-type integ-test --base-path DUMMY_PUBLIC_ARTIFACT_URL/dummy_integ_test/1.3.0/29/linux/x64/tar  '))
        assertThat(getShellCommands('sh', 'report.sh'), hasItems('./report.sh tests/data/opensearch-dashboards-1.3.0-test.yml --artifact-paths opensearch=https://ci.opensearch.org/ci/dbc/distribution-build-opensearch/1.3.0/29/linux/x64/tar opensearch-dashboards=https://ci.opensearch.org/ci/dbc/distribution-build-opensearch-dashboards/1.3.0/25b38c278cdd45efa583765d8ba76346/linux/x64/tar --test-run-id 1234 --test-type integ-test --base-path DUMMY_PUBLIC_ARTIFACT_URL/dummy_integ_test/1.3.0/25b38c278cdd45efa583765d8ba76346/linux/x64/tar  '))
        assertThat(getShellCommands('sh', 'report.sh'), hasItems('./report.sh tests/data/opensearch-dashboards-1.3.0-test.yml --artifact-paths opensearch=https://ci.opensearch.org/ci/dbc/distribution-build-opensearch/1.3.0/29/linux/x64/tar opensearch-dashboards=https://ci.opensearch.org/ci/dbc/distribution-build-opensearch-dashboards/1.3.0/25b38c278cdd45efa583765d8ba76346/linux/x64/tar --test-run-id 1234 --test-type integ-test --base-path DUMMY_PUBLIC_ARTIFACT_URL/dummy_integ_test/1.3.0/25b38c278cdd45efa583765d8ba76346/linux/x64/tar --release-candidate 100 '))
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
