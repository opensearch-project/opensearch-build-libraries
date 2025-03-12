/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/** Library to create, update and close GitHub issue across opensearch-project repositories for integration test failures.
 @param Map args = [:] args A map of the following parameters
 @param args.inputManifestPath <required> - Path to build manifest.
 @param args.distributionBuildNumber <optional> - Distribution build number used to run integTest. Defaults to latest build for given version.
 */

import jenkins.ComponentBuildStatus
import jenkins.ComponentIntegTestStatus
import jenkins.CreateIntegTestMarkDownTable
import jenkins.ReleaseMetricsData

void call(Map args = [:]) {
    def inputManifest = readYaml(file: args.inputManifestPath)
    def version = inputManifest.build.version
    def qualifier = "None"
    if (inputManifest.build.qualifier) {
        qualifier = inputManifest.build.qualifier
    }
    def product = inputManifest.build.name
    def integTestIndexName = 'opensearch-integration-test-results'
    def buildIndexName = 'opensearch-distribution-build-results'
    def releaseIndexName = 'opensearch_release_metrics'

    List<String> failedComponents = []
    List<String> passedComponents = []

    withCredentials([
            string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
            string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')]) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN
            def distributionBuildNumber = args.distributionBuildNumber ?: new ComponentBuildStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, buildIndexName, product, version, qualifier, this).getLatestDistributionBuildNumber().toString()
            ComponentIntegTestStatus componentIntegTestStatus = new ComponentIntegTestStatus(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, integTestIndexName, product, version, qualifier, distributionBuildNumber, this)
            ReleaseMetricsData releaseMetricsData = new ReleaseMetricsData(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, version, releaseIndexName, this)
            println('Distribution Build Number: ' + distributionBuildNumber)
            passedComponents = componentIntegTestStatus.getComponents('passed')
            failedComponents = componentIntegTestStatus.getComponents('failed')

            failedComponents = failedComponents.unique()
            passedComponents = passedComponents.unique()
            println('Failed Components: ' + failedComponents)
            println('Passed Components: ' + passedComponents)

            for (component in inputManifest.components) {
                if (!failedComponents.isEmpty() && failedComponents.contains(component.name)) {
                    println("Integration test failed for ${component.name}, creating github issue")
                    def testData = []
                    println('Retrieving failed component data for '+ component.name)
                    def queryData = componentIntegTestStatus.getComponentIntegTestFailedData(component.name)
                    def totalHits = queryData.hits.hits.collect { it._source }
                    totalHits.each { hit ->
                        String metricsVisualizationUrl = getMetricsVisualizationUrl(hit.distribution, hit.architecture, version, component.name)
                        def rowData = [
                                platform                 : hit.platform,
                                distribution             : hit.distribution,
                                architecture             : hit.architecture,
                                test_report_manifest_yml : hit.test_report_manifest_yml,
                                integ_test_build_url     : hit.integ_test_build_url,
                                distribution_build_number: distributionBuildNumber,
                                rc_number                : hit.rc_number,
                                metrics_visualization_url: metricsVisualizationUrl
                            ]
                        testData << rowData
                    }
                    println('Retrieving release owner(s) for '+ component.name)
                    List releaseOwners = releaseMetricsData.getReleaseOwners(component.name)
                    def markdownContent = new CreateIntegTestMarkDownTable(version).create(testData, releaseOwners)
                    createGithubIssue(
                            repoUrl: component.repository,
                            issueTitle: "[AUTOCUT] Integration Test Failed for ${component.name}-${version}",
                            issueBody: markdownContent,
                            label: "autocut,v${version}",
                            issueEdit: true
                    )
                    sleep(time: 3, unit: 'SECONDS')
                }
                if (!passedComponents.isEmpty() && passedComponents.contains(component.name) && !failedComponents.contains(component.name)) {
                    println("Integration tests passed for ${component.name}, closing github issue")
                    ghIssueBody = """Closing the issue as the integration tests for ${component.name} passed for version: **${version}**.""".stripIndent()
                    closeGithubIssue(
                            repoUrl: component.repository,
                            issueTitle: "[AUTOCUT] Integration Test Failed for ${component.name}-${version}",
                            closeComment: ghIssueBody
                    )
                    sleep(time: 3, unit: 'SECONDS')
                }
            }
        }
    }
}


def getMetricsVisualizationUrl(String distribution, String architecture, String version, String component) {
    String baseUrl = "https://metrics.opensearch.org/_dashboards/app/dashboards?security_tenant=global#/view/21aad140-49f6-11ef-bbdd-39a9b324a5aa"
    String queryParams = "?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-30d,to:now))&_a=(description:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',"
    String filterTemplate = "filters:!(('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:version,negate:!f,params:(query:'${version}'),type:phrase),query:(match_phrase:(version:'${version}'))),('\$state':(store:appState),meta:(alias:!n,disabled:!f,index:d90d2ba0-8fe0-11ef-a168-f19b1bbc360c,key:component,negate:!f,params:(query:${component}),type:phrase),query:(match_phrase:(component:${component})))),fullScreenMode:!f,options:(hidePanelTitles:!f,useMargins:!t),query:(language:kuery,query:''),timeRestore:!t,title:'OpenSearch%20Release%20Build%20and%20Integration%20Test%20Results',viewMode:view)"

    Map<String, String> panelIds = [
            'tar_x64': 'ddafb9c5-2d35-482a-9c61-1ba78b67f406',
            'tar_arm64': 'c570bdfd-3122-4e31-a02d-2130d797d9fc',
            'deb_x64': '5743d5c4-be75-49b9-a81f-fef3f805ad99',
            'deb_arm64': '7a6ee111-1c99-4f96-9a3f-c0f248181980',
            'rpm_x64': '94f0246f-4246-4f05-ba11-b3e22836b8e7',
            'rpm_arm64': 'eae6bad4-cffc-4672-a688-14155229ea63',
            'windows_x64': 'a57afb35-8d97-4641-9b07-64ff614dab00'
    ]

    String key = "${distribution}_${architecture}"
    String expandedPanelId = panelIds[key]

    if (expandedPanelId) {
        return baseUrl + queryParams + "expandedPanelId:${expandedPanelId}," + filterTemplate
    } else {
        println("Unknown ${distribution}_${architecture}")
        return 'null'
    }
}
