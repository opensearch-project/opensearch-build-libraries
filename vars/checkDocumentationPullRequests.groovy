/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
/**
 * Library to check documentation pull requests per release.
 * @param Map args = [:] args A map of the following parameters
 * @param args.version <required> - Release version to track the documentation PRs for.
 */
void call(Map args = [:]) {
    def secret_github_bot = [
        [envVar: 'GITHUB_USER', secretRef: 'op://opensearch-infra-secrets/github-bot/ci-bot-username'],
        [envVar: 'GITHUB_TOKEN', secretRef: 'op://opensearch-infra-secrets/github-bot/ci-bot-token']
    ]

    // Validate Parameters
    validateParameters(args)
    def versionTokenize = args.version.tokenize('-')
    // Qualifiers are not a part of the labels in GitHub. Ignoring it.
    def version = versionTokenize[0]

    withSecrets(secrets: secret_github_bot){
        def openPRs = sh(
                script: "gh pr list --repo opensearch-project/documentation-website --state open --label v${version} -S \"-label:\\\"6 - Done but waiting to merge\\\"\" --json url --jq '.[].url'",
                returnStdout: true
        )
        if (openPRs) {
            echo("Documentation pull requests pending to be merged: \n${openPRs}")
        } else {
            echo("No open pull requests found!")
        }
    }
}

/**
 * Validates input parameters
 */
private void validateParameters(Map args) {
    if (!args.version) {
        error ('Version is required parameter.')
    }
}
