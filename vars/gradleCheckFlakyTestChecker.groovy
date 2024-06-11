/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

import gradlecheck.FetchPostMergeFailedTestClass
import gradlecheck.FetchPostMergeTestGitReference
import gradlecheck.FetchPostMergeFailedTestName
import gradlecheck.FetchTestPullRequests
import gradlecheck.CreateMarkDownTable

void call(Map args = [:]) {
    withCredentials([
            string(credentialsId: 'jenkins-health-metrics-account-number', variable: 'METRICS_HOST_ACCOUNT'),
            string(credentialsId: 'jenkins-health-metrics-cluster-endpoint', variable: 'METRICS_HOST_URL')
    ]) {
        withAWS(role: 'OpenSearchJenkinsAccessRole', roleAccount: "${METRICS_HOST_ACCOUNT}", duration: 900, roleSessionName: 'jenkins-session') {
            def metricsUrl = env.METRICS_HOST_URL
            def awsAccessKey = env.AWS_ACCESS_KEY_ID
            def awsSecretKey = env.AWS_SECRET_ACCESS_KEY
            def awsSessionToken = env.AWS_SESSION_TOKEN
            def postMergeFailedTests = new FetchPostMergeFailedTestClass(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this).getPostMergeFailedTestClass()
            postMergeFailedTests.each { failedTest ->
                def testData = []
                def allPullRequests = []
                def postMergeTestGitReference = new FetchPostMergeTestGitReference(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this).getPostMergeTestGitReference(failedTest)
                postMergeTestGitReference.each { gitReference ->
                    def failedTestNames = new FetchPostMergeFailedTestName(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this).getPostMergeFailedTestName(failedTest, gitReference)
                    def testNames = failedTestNames.aggregations.test_name_keyword_agg.buckets.collect { it.key }
                    def buildNumber = failedTestNames.aggregations.build_number_agg.buckets.collect { it.key }
                    def pullRequests = failedTestNames.aggregations.pull_request_agg.buckets.collect { it.key }
                    allPullRequests.addAll(pullRequests)
                    def rowData = [
                            gitReference: gitReference,
                            pullRequestLink: pullRequests.collect { pr -> "[${pr}](https://github.com/opensearch-project/OpenSearch/pull/${pr})" }.join('<br><br>'),
                            buildDetailLink: buildNumber.collect { build -> "[${build}](https://build.ci.opensearch.org/job/gradle-check/${build}/testReport/)" }.join('<br><br>'),
                            testNames: testNames.collect { testName -> "`${testName}`" }
                    ]
                    testData << rowData
                }
                def testNameAdditionalPullRequests = new FetchTestPullRequests(metricsUrl, awsAccessKey, awsSecretKey, awsSessionToken, this).getTestPullRequests(failedTest).findAll { !allPullRequests.contains(it) }
                def markdownTable = new CreateMarkDownTable(failedTest, testData, testNameAdditionalPullRequests).createMarkdownTable()
                writeFile file: "${failedTest}.md", text: markdownTable
                createGithubIssue(
                        repoUrl: "https://github.com/opensearch-project/OpenSearch",
                        issueTitle: "[AUTOCUT] Gradle Check Flaky Test Report for ${failedTest}",
                        issueBodyFile: "${failedTest}.md",
                        label: 'autocut,>test-failure',
                        issueEdit: true
                )
            }
        }
    }
}

