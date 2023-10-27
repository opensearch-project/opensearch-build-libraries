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
 @param args.daysToReOpen <optional> - Look for a closed Github issues older than `daysToReOpen`.
 */

void call(Map args = [:]) {
    label = args.label ?: 'autocut'
    daysToReOpen = args.daysToReOpen ?: '3'
    try {
        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
            def openIssue = sh(
                    script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title\" --label ${label} --json number --jq '.[0].number'",
                    returnStdout: true
            ).trim()

            def currentDayMinusDaysToReOpen = sh(
                script: "date -d \"${daysToReOpen} days ago\" +'%Y-%m-%d'",
                returnStdout: true
            ).trim()

            def closedIssue = sh(
                    script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title is:closed closed:>=${currentDayMinusDaysToReOpen}\" --label ${label} --json number --jq '.[0].number'",
                    returnStdout: true
            ).trim()

            if (openIssue) {
                println('Issue already exists, adding a comment')
                sh(
                   script: "gh issue comment ${openIssue} --repo ${args.repoUrl} --body \"${args.issueBody}\"",
                   returnStdout: true
                )
            }
            else if (!openIssue && closedIssue) {
                println("Re-opening a recently closed issue and commenting on it")
                sh(
                   script: "gh issue reopen --repo ${args.repoUrl} ${closedIssue}",
                   returnStdout: true
                )
                sh(
                   script: "gh issue comment ${closedIssue} --repo ${args.repoUrl} --body \"${args.issueBody}\"",
                   returnStdout: true
                )
            }
            else {
                println("Creating new issue")
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