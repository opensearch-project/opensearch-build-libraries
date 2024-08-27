/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to publish artifacts to NPM registry under @opensearch-project namespace.
@param Map args = [:] args A map of the following parameters
@param args.publicationType <required> - github (will clone the repository at triggered tag and url and publish), artifact (Needs artifactPath arg to the URL or local artifact that needs to be published)
@param args.artifactPath <required with publicationType: artifact > - URL or local Path to the artifact that needs to be publish to NPM.See supported artifacts https://docs.npmjs.com/cli/v9/commands/npm-publish?v=true#description for more details.
*/
void call(Map args = [:]) {
    parameterCheck(args.publicationType, args.artifactPath)
    artifactPath = args.artifactPath ?: ''
    if (args.publicationType == 'github') {
        checkout([$class: 'GitSCM', branches: [[name: "${env.tag}" ]], userRemoteConfigs: [[url: "${env.repository}" ]]])
    }
    def npmTag = getNpmTag("${env.tag}")

    withCredentials([string(credentialsId: 'jenkins-opensearch-publish-to-npm-token', variable: 'NPM_TOKEN')]) {
        sh """
            npm set registry "https://registry.npmjs.org"
            npm set //registry.npmjs.org/:_authToken ${NPM_TOKEN}
            npm publish ${artifactPath} --dry-run && npm publish ${artifactPath} --access public --tag ${npmTag}
        """
    }
    println('Cleaning up')
    sh """rm -rf ${WORKSPACE}/.nvmrc && rm -rf ~/.nvmrc"""
}

void parameterCheck(String publicationType, String artifactPath) {
    allowedPublicationType = ['github', 'artifact']
    if (!allowedPublicationType.contains(publicationType)) {
        currentBuild.result = 'ABORTED'
        error('Invalid publicationType. publicationType can either be github or artifact')
    }
    if (publicationType == 'artifact' && !artifactPath) {
        currentBuild.result = 'ABORTED'
        error('publicationType: artifact needs an artifactPath. Please provide artifactPath argument. See supported artifacts https://docs.npmjs.com/cli/v9/commands/npm-publish?v=true#description for more details')
    }
    if (publicationType == 'github' && artifactPath) {
        currentBuild.result = 'ABORTED'
        error('publicationType: github does take any argument with it.')
    }
}

String getNpmTag(String githubTag) {
    def matcher = githubTag =~ /-(\w+)\./
    def npmTag = matcher ? matcher[0][1] : 'latest'
    println("Tagging the release as '${npmTag}'")
    return npmTag
}