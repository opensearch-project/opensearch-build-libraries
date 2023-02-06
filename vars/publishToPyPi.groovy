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
@param args.credentialId <required> - Credential id consisting token for publishing the package
@param args.artifactsPath <optional> - The directory containing distribution files to upload to the repository. Defaults to 'dist/*'
*/
void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@1.6.0', retriever: legacySCM(scm))
    String releaseArtifactsDir = args.artifactsPath ? "${WORKSPACE}/${args.artifactsPath}" : "${WORKSPACE}/dist"

    signArtifacts(
        artifactPath: releaseArtifactsDir,
        sigtype: '.asc',
        platform: 'linux'
    )

    withCredentials([usernamePassword(credentialsId: args.credentialId, usernameVariable: 'TWINE_USERNAME', passwordVariable: 'TWINE_PASSWORD')]) {
            sh """twine upload -r pypi ${releaseArtifactsDir}/*"""
    }
}
