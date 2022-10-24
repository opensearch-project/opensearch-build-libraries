/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to publish artifacts to NPM registry under @opensearch-project namespace
@param Map args = [:] args A map of the following parameters
@param args.repository <required> - Repository to be used to publish the artifact to npm
@param args.tag <required> - Tag reference to be used to publish the artifact
*/
void call(Map args = [:]) {

    checkout([$class: 'GitSCM', branches: [[name: "${args.tag}" ]], userRemoteConfigs: [[url: "${args.repository}" ]]])
    
    withCredentials([string(credentialsId: 'publish-to-npm-token', variable: 'NPM_TOKEN')]){
        sh """npm set registry "https://registry.npmjs.org"; npm set //registry.npmjs.org/:_authToken ${NPM_TOKEN}; npm publish --dry-run && npm publish --access public"""
    }
}