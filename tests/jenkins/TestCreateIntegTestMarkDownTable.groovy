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
        def releaseOwners = ['foo', 'bar']
        def tableData = [
                [
                    architecture:"x64", 
                    distribution:"tar", 
                    integ_test_build_url:"some_integ_test_url", 
                    platform:"linux", 
                    test_report_manifest_yml:"test_report_dummy_url",
                    distribution_build_number: '1234',
                    rc_number: 1,
                    metrics_visualization_url: "some_url"
                    ], 
                [
                    architecture:"arm64", 
                    distribution:"tar", 
                    integ_test_build_url:"integ_test_dummy_url", 
                    platform:"linux", 
                    test_report_manifest_yml:"test_report_2_dummy_url",
                    distribution_build_number: '1234',
                    rc_number: 1,
                    metrics_visualization_url: "some_other_url"
                    ]
            ]
        def createIntegTestMarkDownTable = new CreateIntegTestMarkDownTable(version)
        def result = createIntegTestMarkDownTable.create(tableData, releaseOwners)
        def expectedOutput = """
### Integration Test Failed for version 2.18.0. See the specifications below:

#### Details

| Platform | Dist | Arch | Dist Build No. | RC | Test Report | Workflow Run | Failing tests |
|----------|------|------|----------------|----|-------------|--------------|---------------|
| linux | tar | x64 | 1234 | 1 | test_report_dummy_url | some_integ_test_url | [Check metrics](some_url) |
| linux | tar | arm64 | 1234 | 1 | test_report_2_dummy_url | integ_test_dummy_url | [Check metrics](some_other_url) |

Check out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).

Tagging the release owners to take a look @foo @bar"""
        assert result == expectedOutput
    }
}
