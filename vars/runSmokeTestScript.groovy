/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/**@
 * Contains logic to execute the smoke test workflow
 *
 * @param args A map of the following parameters
 * @param args.testManifest <required> Test manifest file location
 * @param args.buildManifest <required> Build manifest file location
 * @param args.buildId <required> Build ID of the distribution artifacts
 * @param args.switchUserNonRoot <Required> Switch to non root user if true.
 * @param args.jobName <optional> Job name that triggered the workflow. 'distribution-build-opensearh' by default.
*/
void call(Map args = [:]) {
    String jobName = args.jobName ?: 'distribution-build-opensearch'
    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    def buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: args.buildManifest))
    String artifactRootUrl = buildManifest.getArtifactRootUrl(jobName, args.buildId)
    echo "Artifact root URL: ${artifactRootUrl}"

    String switchUser = args.switchUserNonRoot ?: 'false'
    if (! (switchUser == 'true' || switchUser == 'false')) {
        echo "args.switchUserNonRoot is not valid. Exit the program."
        System.exit(1)
    }
    echo "Switch User to Non-Root (uid=1000): ${switchUser}"

    String switchCommandStart = switchUser == 'true' ? "su `id -un 1000` -c \"" : ""
    String switchCommandEnd = switchUser == 'true' ? '"' : ''

    String paths = "opensearch=${artifactRootUrl}"
    echo "Paths: ${paths}"

    sh([
            switchCommandStart,
            './test.sh',
            'smoke-test',
            "${args.testManifest}",
            "--test-run-id ${env.BUILD_NUMBER}",
            "--paths ${paths}",
            switchCommandEnd,
    ].join(' '))
}
