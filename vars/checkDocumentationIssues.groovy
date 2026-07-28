/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import utils.TemplateProcessor

/**
 * Library to check documentation issues per release.
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <required> - Release version to track the documentation PRs for.
 * @param args.action <optional> - Action to be performed. Default is 'check'. Acceptable values are 'check' and 'notify'.
 * @return List of open documentation issue URLs without a linked PR (empty when none are open).
 */
List<String> call(Map args = [:]) {
    def secret_github_bot = [
        [envVar: 'GITHUB_USER', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-username'],
        [envVar: 'GITHUB_TOKEN', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-token']
    ]

    String action = args.action ?: 'check'
    // Validate Parameters
    validateParameters(args)
    def versionTokenize = args.version.tokenize('-')
    // Qualifiers are not a part of the labels in GitHub. Ignoring it.
    def version = versionTokenize[0]

    List<String> openIssuesList = []
    withSecrets(secrets: secret_github_bot){
        def openIssues = sh(
                script: "gh issue list --repo opensearch-project/documentation-website --state open --label v${version} -S \"-linked:pr\" --json url --jq '.[].url'",
                returnStdout: true
        )
        if (openIssues) {
            openIssuesList = openIssues.readLines()
            echo("Open documentation issues found. Issue URLs: ${openIssuesList}")
            if(action == 'notify') {
                notifyTheOwners(openIssuesList)
            }
        } else {
            echo("No open documentation issues found without a linked PR!")
        }
    }

    return openIssuesList
}

/**
 * Validates input parameters
 */
private void validateParameters(Map args) {
    if (!args.version) {
        error ('Version is required parameter.')
    }
    List<String> validActions = ['check', 'notify']
    if (!validActions.contains(args.action.toString())) {
        error "Invalid action '${args.action}'. Valid values: ${validActions.join(', ')}"
    }
}

/**
 * Get author or assignee of the issue
 * @param issueUrl
 * @return assignee or author
 */
private String getAuthorOrAssignee(String issueUrl) {
    try {
        def assignee = sh(
                script: "gh issue view ${issueUrl} --repo opensearch-project/documentation-website --json assignees --jq '.assignees[0].login'",
                returnStdout: true
        ).trim()
        if (!isNullOrEmpty(assignee)) {
            return assignee
        } else {
            def author = sh(
                    script: "gh issue view ${issueUrl} --repo opensearch-project/documentation-website --json author --jq '.author.login'",
                    returnStdout: true
            ).trim()
            return author
        }
    } catch (Exception e) {
        error "Failed to get author or assignee for issue ${issueUrl}: ${e.getMessage()}"
    }
}

/**
 * Notify the owners about the possible actions
 * @param openIssuesList
 * @return
 */
private void notifyTheOwners(ArrayList openIssuesList) {
    openIssuesList.each { issueUrl ->
        def owner = getAuthorOrAssignee(issueUrl.toString())
        def bindings = [
                OWNER: owner
        ]
        def githubCommentBody = new TemplateProcessor(this).process("release/documentation-issues-template.md", bindings, "${WORKSPACE}")
        sh(
                script: "gh issue comment ${issueUrl} --repo opensearch-project/documentation-website --body-file ${githubCommentBody}",
                returnStdout: true
        )
    }
}

private boolean isNullOrEmpty(String str) { return (str == null || str.allWhitespace || str.isEmpty()) }
