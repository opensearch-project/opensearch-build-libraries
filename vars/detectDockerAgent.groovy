/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/**@
 * Contains logic to detect the Docker Agent and Images
 *
 * @param args A map of the following parameters
 * @param args.manifest <optional> The input manifest path, default to "manifests/${INPUT_MANIFEST}"
 * @param args.distribution <optional> The distribution name, default to "tar"
 * @param args.platform <optional> The platform name, default to "linux"
 */
Map call(Map args = [:]) {
    def lib = library(identifier: "jenkins@10.1.0", retriever: legacySCM(scm))
    String manifest = args.manifest ?: "manifests/${INPUT_MANIFEST}"
    String distribution = args.distribution ?: "tar"
    String platform = args.platform ?: "linux"
    echo "manifest: ${manifest}, dist: ${distribution}, plat: ${platform}"

    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: manifest))
    def schemaVersion = inputManifest.schemaVersion ?: "None"
    echo "InputManifest SchemaVersion: ${schemaVersion}"

    def dockerImage = 'opensearchstaging/ci-runner:ci-runner-al2-opensearch-build-v1'
    def dockerArgs = '-e JAVA_HOME=/opt/java/openjdk-21'  // Using default javaVersion as openjdk-21
    def javaVersion = 'openjdk-21'

    if (schemaVersion != 'None' && schemaVersion.toDouble() < 1.2) {
        // echo "InputManifest SchemaVersion < 1.2"
        dockerImage = inputManifest.ci?.image?.name ?: dockerImage
        dockerArgs = inputManifest.ci?.image?.args ?: dockerArgs
    }
    else {
        // echo "InputManifest SchemaVersion >= 1.2"
        dockerImage = inputManifest?.ci?.images?.get(platform)?.get(distribution)?.name ?: dockerImage
        dockerArgs = inputManifest?.ci?.images?.get(platform)?.get(distribution)?.args ?: dockerArgs
    }

    // Get java version from the dockerArgs
    java.util.regex.Matcher jdkMatch = (dockerArgs =~ /openjdk-\d+/)
    if (jdkMatch.find()) {
        def jdkMatchLine = jdkMatch[0]
        javaVersion = jdkMatchLine
    }

    echo "Using Docker image ${dockerImage}, args: ${dockerArgs}, java version: ${javaVersion}"

    return [
        image: dockerImage,
        args: dockerArgs,
        javaVersion: javaVersion
    ]
}
