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
 @param args.repoUrl <required> - GitHub repository URL to create issue
 @param args.issueTitle <required> - GitHub issue title
 @param args.closeComment <required> - GitHub issue leave a closing comment
 */
void call(Map args = [:]) {
    try {
        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
            def issuesNumber = sh(
                    script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title\" --json number --jq '.[0].number'",
                    returnStdout: true
            ).trim()
            if (!issuesNumber.isEmpty()) {
                def repoPath = args.repoUrl.replaceFirst("https://github.com/", "").replaceAll("\\.git\$", "")
                sh(
                        script: "gh issue close ${issuesNumber} -R ${repoPath} --comment \"${args.closeComment}\"",
                        returnStdout: true
                )
            } else {
                println("No open distribution failure AUTOCUT issues found that needs to be closed for the repo ${args.repoUrl}")
            }
        }
    } catch (Exception ex) {
        error("Unable to close GitHub issue for ${args.repoUrl}", ex.getMessage())
    }
}