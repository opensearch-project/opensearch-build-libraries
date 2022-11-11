/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to publish artifacts to PyPi registry with OpenSearch as maintainer
@param Map args = [:] args A map of the following parameters
@param args.repository <required> - Repository to be used to publish the artifact to npm
@param args.tag <required> - Tag reference to be used to publish the artifact
@param args.dir <optional> - The directory containing distribution files to upload to the repository. Defaults to 'dist/*'
@param args.signArtifacts <optional> - Sign the artifacts to be uploaded to PyPi. Defaults to 'true'
*/
void call(Map args = [:]) {

    checkout([$class: 'GitSCM', branches: [[name: "${args.tag}" ]], userRemoteConfigs: [[url: "${args.repository}" ]]])

    if (args.signArtifacts) {
        lib = library(identifier: 'jenkins@pypi', retriever: legacySCM(scm))
        signArtifacts(
            artifactPath: args.dir,
            sigtype: '.asc',
            platform: 'linux'
        )
    }

    withCredentials([usernamePassword(credentialsId: 'jenkins-opensearch-pypi-username', usernameVariable: 'TWINE_USERNAME', passwordVariable: 'TWINE_PASSWORD')]) {
            def dist = args.dir ?: 'dist/*'
            sh """twine upload -r pypi ${dist}"""
        }
}
