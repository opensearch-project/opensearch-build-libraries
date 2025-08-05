/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to publish rust artifacts to crates.io registry
@param Map args = [:] args A map of the following parameters
@param args.repository <required> - GitHub repository (source code) for publishing the artifacts to crates.io
@param args.tag <required> - Associated GitHub tag
@param args.packageToPublish <optional> - By default, the package in the current working directory is selected. The packageToPublish flag can be used to choose a different package in the workspace.
*/
void call(Map args = [:]) {
    parameterCheck(args.repository, args.tag)
    String packageToPublish = args.packageToPublish ? "-p ${args.packageToPublish}" : ''

    checkout([$class: 'GitSCM', userRemoteConfigs: [[url: "${args.repository}" ]], branches: [[name: "${args.tag}" ]]])

    def secret_rust = [
        [envVar: 'API_TOKEN', secretRef: 'op://opensearch-infra-secrets/rust/crates-api-token']
    ]

    withSecrets(secrets: secret_rust){
        sh "cargo publish ${packageToPublish} --dry-run && cargo publish ${packageToPublish} --token ${API_TOKEN}"
    }
}

void parameterCheck(String repository, String tag) {
    // Will error out out if either or both are not present
    if (!repository || !tag) {
        currentBuild.result = 'ABORTED'
        error('repository and tag arguments are required.')
    }
}
