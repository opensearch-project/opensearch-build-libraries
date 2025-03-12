/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/**@
 * Contains logic to execute the integration test workflow
 *
 * @param args A map of the following parameters
 * @param args.componentName <required> The plugin/component name
 * @param args.switchUserNonRoot <required> Run as root user
 * @param args.testManifest <required> Test manifest file location
 * @param args.jobName <optional> Job name that triggered the workflow.
 * @param args.localPath <optional> Local path for downloaded build artifacts
 * @param args.ciGroup <optional> The particular ci-group number to run the integration tests for
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))

    String jobName = args.jobName ?: 'distribution-build-opensearch'
    def buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: args.buildManifest))

    String architecture = buildManifest.getArtifactArchitecture()
    String distribution = buildManifest.getDistribution()
    String platform = buildManifest.getArtifactPlatform()
    String filename = buildManifest.build.getFilename()
    echo "Start integTest on: " + distribution + " " + architecture + " " + platform

    def javaVersion = (filename == 'opensearch') ? detectTestDockerAgent(testManifest: args.testManifest).javaVersion : ''
    String javaHomeCommand = (javaVersion != '' && platform != 'windows') ? "JAVA_HOME=/opt/java/${javaVersion}" : ''
    if (filename == 'opensearch' && platform == 'windows') { // Windows use scoop to switch the Java Version
        String javaVersionNumber = javaVersion.replaceAll("[^0-9]", "") // Only get number
        echo("Switching to Java ${javaVersionNumber} on Windows Docker Container")
        sh("scoop reset `scoop list jdk | cut -d ' ' -f1 | grep ${javaVersionNumber} | head -1`")
    }
    echo "Possible Java Home: ${javaHomeCommand}"

    String buildId = buildManifest.build.id
    echo "Build Id: ${buildId}"

    String artifactRootUrl = buildManifest.getArtifactRootUrl(jobName, buildId)
    echo "Artifact root URL: ${artifactRootUrl}"

    String localPath = args.localPath ?: 'None'
    String paths = generatePaths(buildManifest, artifactRootUrl, localPath)
    echo "Paths: ${paths}"

    String basePath = generateBasePaths(buildManifest)
    echo "Base Path ${basePath}"

    String component = args.componentName
    echo "Component: ${component}"

    String switchUser = args.switchUserNonRoot ?: 'false'
    if (! switchUser.equals('true') && ! switchUser.equals('false')) {
        echo "args.switchUserNonRoot can only be 'true' or 'false', exit."
        System.exit(1)
    }
    echo "Switch User to Non-Root (uid=1000): ${switchUser}"

    // Avoid issue related to docker ENV keyword is not correctly interpreted in su commands
    // https://github.com/opensearch-project/opensearch-build-libraries/issues/197
    String switchCommandStart = switchUser.equals('true') ? "su `id -un 1000` -c \"env PATH=\$PATH $javaHomeCommand" : "env PATH=\$PATH $javaHomeCommand"
    String switchCommandEnd = switchUser.equals('true') ? '"' : ''

    String testCommand =
    [
        switchCommandStart,
        './test.sh',
        'integ-test',
        "${args.testManifest}",
        "--component ${component}",
        isNullOrEmpty(args.ciGroup.toString()) ? "" : "--ci-group ${args.ciGroup}",
        "--test-run-id ${env.BUILD_NUMBER}",
        "--paths ${paths}",
        "--base-path ${basePath}",
        switchCommandEnd,
    ].join(' ')

    echo "Run command: " + testCommand
    sh(testCommand)
}

String generatePaths(buildManifest, artifactRootUrl, localPath) {
    String name = buildManifest.build.name
    String version = buildManifest.build.version
    String platform = buildManifest.build.platform
    String architecture = buildManifest.build.architecture
    String distribution = buildManifest.build.distribution

    String latestOpenSearchArtifactRootUrl = "https://ci.opensearch.org/ci/dbc/distribution-build-opensearch/${version}/latest/${platform}/${architecture}/${distribution}"
    if (localPath.equals('None')) {
        echo "No localPath found, download from url"
        return name == 'OpenSearch' ?
            "opensearch=${artifactRootUrl}" :
            "opensearch=${latestOpenSearchArtifactRootUrl} opensearch-dashboards=${artifactRootUrl}"
    }
    else {
        echo "User provides localPath, use local artifacts: ${localPath}"
        return name == 'OpenSearch' ?
            "opensearch=${localPath}" :
            "opensearch=${localPath} opensearch-dashboards=${localPath}"
    }
}

String generateBasePaths(buildManifest) {
    return ["${env.PUBLIC_ARTIFACT_URL}", "${env.JOB_NAME}", buildManifest.build.version, buildManifest.build.id, buildManifest.build.platform, buildManifest.build.architecture, buildManifest.build.distribution].join("/")
}

boolean isNullOrEmpty(String str) { return (str == null || str.allWhitespace || str.isEmpty() || str == 'null') }
