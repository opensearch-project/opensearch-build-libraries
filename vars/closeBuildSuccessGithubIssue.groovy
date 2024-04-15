/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /** Library to close GitHub issue across opensearch-project repositories.
 @param Map args = [:] args A map of the following parameters
 @param args.message <required> - message retrieved from buildMessage() method.
 @param args.search <required> - Filter the logs based on the passed args.search.
 @param args.inputManifestPath <required> - Path to input manifest.
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@6.4.4', retriever: legacySCM(scm))
    def passMessages = args.message
    def queryString = args.search
    List<String> passedComponents = []
    for (message in passMessages.unique()) {
        java.util.regex.Matcher match = (message =~ /(?<=\b${queryString}\s).*/)
        String matched = match[0]
        println(matched.split(" ")[0].trim())
        passedComponents.add(matched.split(" ")[0].trim())
    }

    def yamlFile = readYaml(file: args.inputManifestPath)
    def currentVersion = yamlFile.build.version

    for (component in yamlFile.components) {
        if (passedComponents.contains(component.name)) {
            println("Component ${component.name} passed, closing github issue")
            ghIssueBody = """Closing the issue as the distribution build for ${component.name} has passed for version: **${currentVersion}**.
                    Please see build log at ${BUILD_URL}consoleFull""".stripIndent()
            closeGithubIssue(
                repoUrl: component.repository,
                issueTitle: "[AUTOCUT] Distribution Build Failed for ${component.name}-${currentVersion}",
                closeComment: ghIssueBody,
                label: "autocut,v${currentVersion}"
            )
            sleep(time:3, unit:'SECONDS')
        }
    }
}
