/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins

class CreateIntegTestMarkDownTable {
    String version

    CreateIntegTestMarkDownTable(String version) {
        this.version = version
    }

    def create(List<Map<String, Object>> tableData, List releaseOwner) {

        def tableHeader = """
### Integration Test Failed for version ${version}. See the specifications below:

#### Details

| Platform | Dist | Arch | Dist Build No. | RC | Test Report | Workflow Run | Failing tests |
|----------|------|------|----------------|----|-------------|--------------|---------------|
"""
        def tableRows = tableData.collect { row ->
            "| ${row.platform} | ${row.distribution} | ${row.architecture} | ${row.distribution_build_number} | ${row.rc_number} | ${row.test_report_manifest_yml} | ${row.integ_test_build_url} | [Check metrics](${row.metrics_visualization_url}) |"}.join("\n")

        def additionalInformation = """
\nCheck out test report manifest linked above for steps to reproduce, cluster and integration test failure logs. For additional information checkout the [wiki](https://github.com/opensearch-project/opensearch-build/wiki/Testing-the-Distribution) and [OpenSearch Metrics Dashboard](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa).
"""
        if(releaseOwner != null && !releaseOwner.isEmpty()) {
            def tagReleaseOwner = releaseOwner.collect{ "@${it}"}.join(' ')
            additionalInformation = additionalInformation + "\nTagging the release owners to take a look ${tagReleaseOwner}"
        }
        return tableHeader + tableRows + additionalInformation
    }

}
