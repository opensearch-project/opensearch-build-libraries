/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

import org.junit.*


class TestCreateIntegTestMarkDownTable  {

    @Test
    void testCreateIntegTestMarkDownTableWithSampleData() {
        def version = "2.18.0"
        def tableData = [
                [
                    architecture:"x64", 
                    distribution:"tar", 
                    integ_test_build_url:"https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6561/display/redirect", 
                    platform:"linux", 
                    test_report_manifest_yml:"https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml"
                    ], 
                [
                    architecture:"arm64", 
                    distribution:"tar", 
                    integ_test_build_url:"https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6560/display/redirect", 
                    platform:"linux", 
                    test_report_manifest_yml:"https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml"
                    ]
            ]
        def createIntegTestMarkDownTable = new CreateIntegTestMarkDownTable(version, tableData)
        def result = createIntegTestMarkDownTable.create()
        def expectedOutput = """
### Integration Test Failed for version 2.18.0. See the specifications below:

#### Details

| Platform | Distribution | Architecture | Test Report Manifest | Workflow Run |
|----------|--------------|--------------|----------------------|--------------|
| linux | tar | x64 | https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/x64/tar/test-results/6561/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6561/display/redirect
| linux | tar | arm64 | https://ci.opensearch.org/ci/dbc/integ-test-opensearch-dashboards/2.18.0/7984/linux/arm64/tar/test-results/6560/integ-test/test-report.yml | https://build.ci.opensearch.org/job/integ-test-opensearch-dashboards/6560/display/redirect

Check out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).
"""
        assert result == expectedOutput
    }
}
