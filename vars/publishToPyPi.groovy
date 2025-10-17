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
    lib = library(identifier: 'jenkins@11.1.2', retriever: legacySCM(scm))
    String releaseArtifactsDir = args.artifactsPath ? "${WORKSPACE}/${args.artifactsPath}" : "${WORKSPACE}/dist"

    signArtifacts(
        artifactPath: releaseArtifactsDir,
        sigtype: '.asc',
        platform: 'linux'
    )

    def secret_pypi_credentials = [
        [envVar: 'TWINE_USERNAME', secretRef: 'op://opensearch-infra-secrets/pypi/twine-username'],
        [envVar: 'TWINE_PASSWORD', secretRef: "op://opensearch-infra-secrets/pypi/${args.credentialId}"]
    ]

    withSecrets(secrets: secret_pypi_credentials){
            sh """twine upload -r pypi ${releaseArtifactsDir}/*"""
    }
}
