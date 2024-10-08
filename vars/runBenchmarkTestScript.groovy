/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/** Library to execute benchmark-test using opensearch-benchmark and opensearch-cluster-cdk
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.command <required> - Name of command to run. 'execute-test' or 'compare'.
 * @param args.bundleManifest <optional> - OpenSearch bundle manifest url.
 * @param args.distributionUrl <optional> - Download link for the OpenSearch bundle tarball.
 * @param args.distributionVersion <optional> - Provide OpenSearch version if using distributionUrl param
 * @param args.endpoint <optional> - Endpoint to the cluster.
 * @param args.insecure <optional> - Force the security of the cluster to be disabled, default is false.
 * @param args.workload <required> - Name of the workload that OpenSearch Benchmark should run, default is nyc_taxis.
 * @param args.singleNode <optional> - Create single node OS cluster, default is true.
 * @param args.minDistribution <optional> - Use min distribution of OpenSearch for cluster, default is false.
 * @param args.use50PercentHeap <optional> - Use 50 percent of physical memory as heap, default is false.
 * @param args.captureNodeStat <optional> - Make opensearch-benchmark to capture node stats during run, default is false
 * @param args.captureSegmentReplicationStat <optional> - Enable opensearch-benchmark to capture segment_replication stat metrics such as replication lag., default is false
 * @param args.suffix <optional> - Suffix to be added to stack name for benchmark test.
 * @param args.managerNodeCount <optional> - Number of manager nodes in multi-node cluster, default is 3.
 * @param args.dataNodeCount <optional> - Number of data nodes in multi-node cluster, default is 2.
 * @param args.clientNodeCount <optional> - Number of client nodes in multi-node cluster, default is 0.
 * @param args.ingestNodeCount <optional> - Number of ingest nodes in multi-node cluster, default is 0.
 * @param args.mlNodeCount <optional> - Number of ml nodes in multi-node cluster, default is 0.
 * @param args.dataInstanceType <optional> - EC2 instance type for data node, defaults to r5.xlarge.
 * @param args.enableRemoteStore <optional> - Enable remote-store feature in OpenSearch cluster
 * @param args.workloadParams <optional> - Additional parameters for benchmark workload type, e.g., number_of_replicas:1,number_of_shards:5.
 * @param args.testProcedure <optional> - Defines a test procedure to use. If empty runs default test procedure for the supplied workload.
 * @param args.excludeTasks <optional> - Defines a comma-separated list of test procedure tasks not to run. Default runs all.
 * @param args.includeTasks <optional> - Defines a comma-separated list of test procedure tasks to run. Default runs all.
 * @param args.dataStorageSize <optional> - Data node ebs storage size, default is 100G.
 * @param args.mlStorageSize <optional> - Ml node ebs storage size, default is 100G.
 * @param args.jvmSysProps <optional> - Custom JVM properties to be set for OS cluster.
 * @param args.userTag <optional> - Additional metadata tags to be added to benchmark run metrics, e.g., run-type:adhoc,arch:x64
 * @param args.configName <optional> - Name of the config file that needs to be downloaded from S3 bucket, default is config.yml.
 * @param args.telemetryParams <optional> - Allows to set parameters for telemetry devices such as node-stat etc., e.g. {"node-stats-include-indices": "true"}
 * @param args.baseline <required> - The baseline TestExecution ID used to compare the contender TestExecution.
 * @param args.contender <required> - The TestExecution ID for the contender being compared to the baseline.
 * @param args.results_format <optional> - Defines the output format for the command line results, either markdown or csv. Default is markdown.
 * @param args.results_numbers_align <optional> - Defines the column number alignment for when the compare command outputs results. Default is right.
 * @param args.results_file <optional> - When provided a file path, writes the compare results to the file indicated in the path.
 * @param args.show_in_results <optional> - Determines whether or not to include the comparison in the results file.
 */
void call(Map args = [:]) {

    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    def buildManifest = null

    if (!isNullOrEmpty(args.bundleManifest as String)){
        buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: args.bundleManifest))
    }

    config_name = isNullOrEmpty(args.config) ? 'config.yml' : args.config
    benchmark_config = 'benchmark.ini'
    withCredentials([string(credentialsId: 'jenkins-aws-account-public', variable: 'AWS_ACCOUNT_PUBLIC'),
                    string(credentialsId: 'jenkins-artifact-bucket-name', variable: 'ARTIFACT_BUCKET_NAME')]) {
        withAWS(role: 'opensearch-test', roleAccount: "${AWS_ACCOUNT_PUBLIC}", duration: 900, roleSessionName: 'jenkins-session') {
            if(isNullOrEmpty(args.endpoint) && args.command == 'execute-test') {
                s3Download(file: 'config.yml', bucket: "${ARTIFACT_BUCKET_NAME}", path: "${BENCHMARK_TEST_CONFIG_LOCATION}/${config_name}", force: true)
            }
            s3Download(file: 'benchmark.ini', bucket: "${ARTIFACT_BUCKET_NAME}", path: "${BENCHMARK_TEST_CONFIG_LOCATION}/${benchmark_config}", force: true)

            /*Added sleep to let the file get downloaded first before write happens. Without the sleep the write is
            happening in parallel to download resulting in file not found error. To avoid pip install conflict errors
            when runnin with and without security run in parallel add enough gap between execution.
            */
        }
    }

    editBenchmarkConfig("${WORKSPACE}/benchmark.ini")

    String command = ''

    if(args.command == 'execute-test') {

        if (args.insecure.toBoolean()) {
            sleep(5)
        } else {
            sleep(120)
        }

        String userTags = getMetadataTags(args.userTag.toString(), buildManifest)

        command = [
            './test.sh',
            'benchmark-test',
            args.command,
            isNullOrEmpty(args.bundleManifest) ? "" : "--bundle-manifest ${args.bundleManifest}",
            isNullOrEmpty(args.distributionUrl) ? "" : "--distribution-url ${args.distributionUrl}",
            isNullOrEmpty(args.distributionVersion) ? "" : "--distribution-version ${args.distributionVersion}",
            isNullOrEmpty(args.endpoint) ? "" : "--cluster-endpoint ${args.endpoint}",
            isNullOrEmpty(args.endpoint) ? "--config ${WORKSPACE}/config.yml" : "",
            "--workload ${args.workload}",
            "--benchmark-config ${WORKSPACE}/benchmark.ini",
            "--user-tag ${userTags}",
            args.insecure?.toBoolean() ? "--without-security" : "",
            isNullOrEmpty(args.username) ? "" : "--username ${args.username}",
            isNullOrEmpty(args.password) ? "" : "--password ${args.password}",
            args.singleNode?.toBoolean() ? "--single-node" : "",
            args.minDistribution?.toBoolean() ? "--min-distribution" : "",
            args.use50PercentHeap?.toBoolean() ? "--use-50-percent-heap" : "",
            args.enableRemoteStore?.toBoolean() ? "--enable-remote-store" : "",
            args.captureNodeStat?.toBoolean() ? "--capture-node-stat" : "",
            args.enableInstanceStorage?.toBoolean() ? "--enable-instance-storage" : "",
            args.captureSegmentReplicationStat?.toBoolean() ? "--capture-segment-replication-stat" : "",
            isNullOrEmpty(args.suffix) ? "" : "--suffix ${args.suffix}",
            isNullOrEmpty(args.managerNodeCount) ? "" : "--manager-node-count ${args.managerNodeCount}",
            isNullOrEmpty(args.dataNodeCount) ? "" : "--data-node-count ${args.dataNodeCount}",
            isNullOrEmpty(args.clientNodeCount) ? "" : "--client-node-count ${args.clientNodeCount}",
            isNullOrEmpty(args.ingestNodeCount) ? "" : "--ingest-node-count ${args.ingestNodeCount}",
            isNullOrEmpty(args.mlNodeCount) ? "" : "--ml-node-count ${args.mlNodeCount}",
            isNullOrEmpty(args.dataInstanceType) ? "" : "--data-instance-type ${args.dataInstanceType}",
            isNullOrEmpty(args.workloadParams) ? "" : "--workload-params '${args.workloadParams}'",
            isNullOrEmpty(args.testProcedure) ? "" : "--test-procedure ${args.testProcedure}",
            isNullOrEmpty(args.excludeTasks) ? "" : "--exclude-tasks ${args.excludeTasks}",
            isNullOrEmpty(args.includeTasks) ? "" : "--include-tasks ${args.includeTasks}",
            isNullOrEmpty(args.additionalConfig) ? "" : "--additional-config ${args.additionalConfig}",
            isNullOrEmpty(args.dataStorageSize) ? "" : "--data-node-storage ${args.dataStorageSize}",
            isNullOrEmpty(args.mlStorageSize) ? "" : "--ml-node-storage ${args.mlStorageSize}",
            isNullOrEmpty(args.jvmSysProps) ? "" : "--jvm-sys-props ${args.jvmSysProps}",
            isNullOrEmpty(args.telemetryParams) ? "" : "--telemetry-params '${args.telemetryParams}'"
        ].join(' ').trim()

    } else if(args.command == 'compare') {
        command = [
            './test.sh',
            'benchmark-test',
            args.command,
            args.baseline,
            args.contender,
            "--benchmark-config ${WORKSPACE}/benchmark.ini",
            isNullOrEmpty(args.suffix) ? "" : "--suffix ${args.suffix}",
            isNullOrEmpty(args.results_format) ? "" : "--results-format=${args.results_format}",
            isNullOrEmpty(args.results_numbers_align) ? "" : "--results-numbers-align=${args.results_numbers_align}",
            isNullOrEmpty(args.results_file) ? "" : "--results-file=${args.results_file}",
            isNullOrEmpty(args.show_in_results) ? "" : "--show-in-results=${args.show_in_results}"
        ].join(' ').trim()
    }

    sh """set +x && ${command}"""

}

void editBenchmarkConfig(String config_file) {
    withCredentials([string(credentialsId: 'benchmark-metrics-datastore-user', variable: 'DATASTORE_USER'),
                     string(credentialsId: 'benchmark-metrics-datastore-password', variable: 'DATASTORE_PASSWORD')]) {
        def file = readFile(file: "${config_file}")
        def contents = file.replace("insert_user_here", "${DATASTORE_USER}")
        contents = contents.replace("insert_password_here", "${DATASTORE_PASSWORD}")
        writeFile file: "${config_file}", text: contents
    }

}

String getMetadataTags(tags, buildManifest) {
    def metadataTags = null
    if (buildManifest != null) {
        metadataTags = "distribution-build-id:${buildManifest.getArtifactBuildId()},arch:${buildManifest.getArtifactArchitecture()}," +
                "os-commit-id:${buildManifest.getCommitId("OpenSearch")}"
    }

    if (!isNullOrEmpty(tags) && buildManifest != null){
        metadataTags = metadataTags + ',' + tags
        return metadataTags
    } else if (!isNullOrEmpty(tags)) {
        return tags
    }
    return metadataTags
}

boolean isNullOrEmpty(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }
