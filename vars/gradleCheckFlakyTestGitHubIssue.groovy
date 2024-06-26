/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to create/edit/skip Flaky Test Report GitHub issue for OpenSearch repo.
 @param Map args = [:] args A map of the following parameters
 @param args.repoUrl <required> - GitHub repository URL to create issue
 @param args.issueTitle <required> - GitHub issue title
 @param args.issueBody <required> - GitHub issue body
 @param args.label <optional> - GitHub issue label to be attached along with 'untriaged'. Defaults to autocut.
 @param args.issueEdit <optional> - Updates the body of the issue, the default if not passed is to add a comment.
 @param args.issueBodyFile <optional> - GitHub issue body from an `.md` file
 */

import gradlecheck.ParseMarkDownTable
import gradlecheck.MarkdownComparator

void call(Map args = [:]) {
    label = args.label ?: 'autocut,>test-failure,flaky-test'
    try {
        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
            def openIssue = sh(
                    script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title\" --json number --jq '.[0].number'",
                    returnStdout: true
            ).trim()


            def closedIssue = sh(
                    script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title is:closed\" --json number --jq '.[0].number'",
                    returnStdout: true
            ).trim()

            if (openIssue) {
                println('Issue already exists, editing the issue body')
                sh(
                        script: "gh issue edit ${openIssue} --repo ${args.repoUrl} --body-file \"${args.issueBodyFile}\"",
                        returnStdout: true
                )
            }
            else if (!openIssue && closedIssue) {
                def existingIssueBody = sh(
                        script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title is:closed\"  --json body --jq '.[0].body'",
                        returnStdout: true
                ).trim()
                def existingTable = new ParseMarkDownTable(existingIssueBody).parseMarkdownTableRows()
                def markdownTable = new ParseMarkDownTable(readFile(args.issueBodyFile)).parseMarkdownTableRows()
                def differences = new MarkdownComparator(markdownTable, existingTable).markdownComparison()
                if (!differences) {
                    println("Not Re-opening the issue as the no change in the Flaky report after the issue is closed")
                } else {
                    println "Differences found:"
                    differences.each { diffRow ->
                        println "Git Reference: ${diffRow['Git Reference']}, " +
                                "Merged Pull Request: ${diffRow['Merged Pull Request']}, " +
                                "Build Details: ${diffRow['Build Details']}, " +
                                "Test Name: ${diffRow['Test Name']}"
                    }
                    sh(
                            script: "gh issue reopen --repo ${args.repoUrl} ${closedIssue}",
                            returnStdout: true
                    )
                    sh(
                            script: "gh issue edit ${closedIssue} --repo ${args.repoUrl} --body-file \"${args.issueBodyFile}\"",
                            returnStdout: true
                    )
                }
            }
            else {
                println("Creating new issue")
                sh(
                        script: "gh issue create --title \"${args.issueTitle}\" --body-file \"${args.issueBodyFile}\" --label \"${label}\" --label \"untriaged\" --repo ${args.repoUrl}",
                        returnStdout: true
                )
            }
        }
    } catch (Exception ex) {
        error("Unable to create GitHub issue for ${args.repoUrl}", ex.getMessage())
    }
}




