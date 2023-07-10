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
 * @param args.bundleManifest <required> - OpenSearch bundle manifest url.
 * @param args.insecure <optional> - Force the security of the cluster to be disabled, default is false.
 * @param args.workload <required> - Name of the workload that OpenSearch Benchmark should run, default is nyc_taxis.
 * @param args.singleNode <optional> - Create single node OS cluster, default is true.
 * @param args.minDistribution <optional> - Use min distribution of OpenSearch for cluster, default is false.
 * @param args.use50PercentHeap <optional> - Use 50 percent of physical memory as heap, default is false.
 * @param args.captureNodeStat <optional> - Make opensearch-benchmark to capture node stats during run, default is false
 * @param args.suffix <optional> - Suffix to be added to stack name for benchmark test.
 * @param args.managerNodeCount <optional> - Number of manager nodes in multi-node cluster, default is 3.
 * @param args.dataNodeCount <optional> - Number of data nodes in multi-node cluster, default is 2.
 * @param args.clientNodeCount <optional> - Number of client nodes in multi-node cluster, default is 0.
 * @param args.ingestNodeCount <optional> - Number of ingest nodes in multi-node cluster, default is 0.
 * @param args.mlNodeCount <optional> - Number of ml nodes in multi-node cluster, default is 0.
 * @param args.enableRemoteStore <optional> - Enable remote-store feature in OpenSearch cluster
 * @param args.workloadParams <optional> - Additional parameters for benchmark workload type, e.g., number_of_replicas:1,number_of_shards:5.
 * @param args.dataStorageSize <optional> - Data node ebs storage size, default is 100G.
 * @param args.mlStorageSize <optional> - Ml node ebs storage size, default is 100G.
 * @param args.jvmSysProps <optional> - Custom JVM properties to be set for OS cluster.
 * @param args.userTag <optional> - Additional metadata tags to be added to benchmark run metrics, e.g., run-type:adhoc,arch:x64
 * @param args.configName <optional> - Name of the config file that needs to be downloaded from S3 bucket, default is config.yml.
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@5.1.0', retriever: legacySCM(scm))
    def buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: args.bundleManifest))

    config_name = isNullOrEmpty(args.config) ? 'config.yml' : args.config
    benchmark_config = 'benchmark.ini'
    withCredentials([string(credentialsId: 'jenkins-aws-account-public', variable: 'AWS_ACCOUNT_PUBLIC'),
                     string(credentialsId: 'jenkins-artifact-bucket-name', variable: 'ARTIFACT_BUCKET_NAME')]) {
        withAWS(role: 'opensearch-test', roleAccount: "${AWS_ACCOUNT_PUBLIC}", duration: 900, roleSessionName: 'jenkins-session') {
            s3Download(file: 'config.yml', bucket: "${ARTIFACT_BUCKET_NAME}", path: "${BENCHMARK_TEST_CONFIG_LOCATION}/${config_name}", force: true)
            s3Download(file: 'benchmark.ini', bucket: "${ARTIFACT_BUCKET_NAME}", path: "${BENCHMARK_TEST_CONFIG_LOCATION}/${benchmark_config}", force: true)

            /*Added sleep to let the file get downloaded first before write happens. Without the sleep the write is
            happening in parallel to download resulting in file not found error
             */
            sleep(5)
        }
    }
    editBenchmarkConfig("${WORKSPACE}/benchmark.ini")
    String userTags = getMetadataTags(args.userTag.toString(), buildManifest)

    sh([
            './test.sh',
            'benchmark-test',
            "--bundle-manifest ${args.bundleManifest}",
            "--config ${WORKSPACE}/config.yml",
            "--workload ${args.workload}",
            "--benchmark-config ${WORKSPACE}/benchmark.ini",
            "--user-tag ${userTags}",
            args.insecure.toBoolean() ? "--without-security" : "",
            args.singleNode.toBoolean() ? "--single-node" : "",
            args.minDistribution.toBoolean() ? "--min-distribution" : "",
            args.use50PercentHeap.toBoolean() ? "--use-50-percent-heap" : "",
            args.enableRemoteStore.toBoolean() ? "--enable-remote-store" : "",
            args.captureNodeStat.toBoolean() ? "--capture-node-stat" : "",
            isNullOrEmpty(args.suffix.toString()) ? "" : "--suffix ${args.suffix}",
            isNullOrEmpty(args.managerNodeCount.toString()) ? "" : "--manager-node-count ${args.managerNodeCount}",
            isNullOrEmpty(args.dataNodeCount.toString()) ? "" : "--data-node-count ${args.dataNodeCount}",
            isNullOrEmpty(args.clientNodeCount.toString()) ? "" : "--client-node-count ${args.clientNodeCount}",
            isNullOrEmpty(args.ingestNodeCount.toString()) ? "" : "--ingest-node-count ${args.ingestNodeCount}",
            isNullOrEmpty(args.mlNodeCount.toString()) ? "" : "--ml-node-count ${args.mlNodeCount}",
            isNullOrEmpty(args.workloadParams.toString()) ? "" : "--workload-params ${args.workloadParams}",
            isNullOrEmpty(args.additionalConfig.toString()) ? "" : "--additional-config ${args.additionalConfig}",
            isNullOrEmpty(args.dataStorageSize.toString()) ? "" : "--data-node-storage ${args.dataStorageSize}",
            isNullOrEmpty(args.mlStorageSize.toString()) ? "" : "--ml-node-storage ${args.mlStorageSize}",
            isNullOrEmpty(args.jvmSysProps.toString()) ? "" : "--jvm-sys-props ${args.jvmSysProps}"
    ].join(' '))

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
    def metadataTags = "distribution-build-id:${buildManifest.getArtifactBuildId()},arch:${buildManifest.getArtifactArchitecture()}," +
            "os-commit-id:${buildManifest.getCommitId("OpenSearch")}"
    if (!isNullOrEmpty(tags)){
        metadataTags = metadataTags + ',' + tags
        return metadataTags
    }
    return metadataTags
}

boolean isNullOrEmpty(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }
