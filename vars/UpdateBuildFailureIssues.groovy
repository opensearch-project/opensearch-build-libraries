/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /** Library to create, update an close GitHub issue across opensearch-project repositories for distribution build failures.
 @param Map args = [:] args A map of the following parameters
 @param args.failureMessages <required> - Failure message retrieved from buildFailureMessage() method.
 @param args.passMessages <required> - Passing message retrieved from buildFailureMessage() method.
 @param args.inputManifestPath <required> - Path to input manifest.
 */
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    def failureMessages = args.failureMessages
    def passMessages = args.passMessages
    def inputManifest = readYaml(file: args.inputManifestPath)
    def currentVersion = inputManifest.build.version

    List<String> failedComponents = []
    List<String> passedComponents = []

    for (message in failureMessages) {
        java.util.regex.Matcher match = (message =~ /(?<=\bError building\s).*/)
        String matched = match[0]
        failedComponents.add(matched.split(' ')[0].split(',')[0].trim())
    }
    failedComponents = failedComponents.unique()
    for (message in passMessages) {
        java.util.regex.Matcher match = (message =~ /(?<=\bSuccessfully built\s).*/)
        String matched = match[0]
        passedComponents.add(matched.split(' ')[0].trim())
    }
    passedComponents = passedComponents.unique()

    for (component in inputManifest.components) {
        if (failedComponents.contains(component.name)) {
            println("Component ${component.name} failed, creating github issue")
            exactComponentFailureMessage = getExactErrorMessage(failureMessages, component.name)
            ghIssueBody = """***Received Error***: **${exactComponentFailureMessage}**.
                    ${component.name} failed during the distribution build for version: ${currentVersion}.
                    Please see build log at ${env.RUN_DISPLAY_URL}.
                    The failed build stage will be marked as unstable(!). Please see ./build.sh step for more details""".stripIndent()
            createGithubIssue(
                repoUrl: component.repository,
                issueTitle: "[AUTOCUT] Distribution Build Failed for ${component.name}-${currentVersion}",
                issueBody: ghIssueBody,
                label: "autocut,v${currentVersion}"
            )
        }
        if (passedComponents.contains(component.name) && !failedComponents.contains(component.name)) {
            println("Component ${component.name} passed, closing github issue")
            ghIssueBody = """Closing the issue as the distribution build for ${component.name} has passed for version: **${currentVersion}**.
                    Please see build log at ${env.RUN_DISPLAY_URL}""".stripIndent()
            closeGithubIssue(
                repoUrl: component.repository,
                issueTitle: "[AUTOCUT] Distribution Build Failed for ${component.name}-${currentVersion}",
                closeComment: ghIssueBody,
                label: "autocut,v${currentVersion}"
            )
        }
        sleep(time:3, unit:'SECONDS')
    }
}

def getExactErrorMessage(FailureMessage, componentName) {
    for (message in FailureMessage) {
        if (message.contains(componentName)) {
            return(message)
        }
    }
}
