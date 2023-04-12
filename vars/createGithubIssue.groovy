/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to create GitHub issue across opensearch-project repositories.
 @param Map args = [:] args A map of the following parameters
 @param args.repoUrl <required> - GitHub repository URL to create issue
 @param args.issueTitle <required> - GitHub issue title
 @param args.issueBody <required> - GitHub issue body
 @param args.label <optional> - GitHub issue label to be attached along with 'untriaged'. Defaults to autocut.
 */
void call(Map args = [:]) {
    label = args.label ?: 'autocut'
    try {
        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
            def issues = sh(
                    script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title\" --label ${label}",
                    returnStdout: true
            )

            if (issues) {
                println('Issue already exists in the repository, skipping.')
            }
            else {
                sh(
                    script: "gh issue create --title \"${args.issueTitle}\" --body \"${args.issueBody}\" --label ${label} --label \"untriaged\" --repo ${args.repoUrl}",
                    returnStdout: true
                )
            }
        }
    } catch (Exception ex) {
        error("Unable to create GitHub issue for ${args.repoUrl}", ex.getMessage())
    }
}