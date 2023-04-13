/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /** Library to create GitHub issue across opensearch-project repositories for distribution build failures.
 @param Map args = [:] args A map of the following parameters
 @param args.message <required> - Failure message retrieved from buildFailureMessage() method.
 @param args.inputManifestPath <required> - Path to input manifest.
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    def failureMessages = args.message
    List<String> failedComponents = []

    if (failureMessages.size() == 1 && failureMessages[0] == 'Build failed') {
        println('No component failed, skip creating github issue.')
    }
    else {
        for (message in failureMessages.unique()) {
            java.util.regex.Matcher match = (message =~ /(?<=\bcomponent\s).*/)
            String matched = match[0]
            println(matched.split(" ")[0].trim())
            failedComponents.add(matched.split(" ")[0].trim())
        }
        /* Due to an existing issue with queryWorkbench plugin breaking OSD during bootstrapping, there are false positive
           issues getting created against OSD repo. Adding a temp check to ignore issue creation against OSD repo in-case
           there are more than 1 failures reported for OSD build.
         */
        if (failedComponents.contains('OpenSearch-Dashboards') && failedComponents.size() > 1) {
            failedComponents.removeElement('OpenSearch-Dashboards')
        }

        def yamlFile = readYaml(file: args.inputManifestPath)
        def currentVersion = yamlFile.build.version

        for (component in yamlFile.components) {
            if (failedComponents.contains(component.name)) {
                println("Component ${component.name} failed, creating github issue")
                compIndex = failedComponents.indexOf(component.name)
                ghIssueBody = """***Received Error***: **${failureMessages[compIndex]}**.
                      The distribution build for ${component.name} has failed for version: ${currentVersion}.
                      Please see build log at ${BUILD_URL}consoleFull""".stripIndent()
                createGithubIssue(
                    repoUrl: component.repository,
                    issueTitle: "[AUTOCUT] Distribution Build Failed for ${component.name}-${currentVersion}",
                    issueBody: ghIssueBody,
                    label: "autocut,v${currentVersion}"
                    )
                sleep(time:3, unit:'SECONDS')
            }
        }
    }
}
