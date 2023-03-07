/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to publish artifacts to Nuget
@param Map args = [:] args A map of the following parameters
@param args.repository <required> - Repository to build the nupkg packages
@param args.tag <required> - Tag reference to be used to publish the artifact
@param args.apiKeyCredentialId <required> - Jenkins Credential ID for API key
@param args.solutionFilePath <required> - Solution File path used to build artifacts
*/


void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@2.1.0', retriever: legacySCM(scm))
    checkout([$class: 'GitSCM', branches: [[name: "${args.tag}" ]], userRemoteConfigs: [[url: "${args.repository}" ]]])
    sh """
    dotnet build ${WORKSPACE}/${args.solutionFilePath} --configuration Release
    find src -name OpenSearch*.dll>${WORKSPACE}/dlls.txt
    """
    dlls = readFile(file: "${WORKSPACE}/dlls.txt").readLines()
    dlls.each { item ->
        signArtifacts(
            artifactPath: item,
            platform: 'windows',
            overwrite: true
            )
        }
    withCredentials([string(credentialsId: "${args.apiKeyCredentialId}", variable: 'API_KEY')]) {
        sh """
            dotnet pack ${WORKSPACE}/${args.solutionFilePath} --configuration Release --no-build
            for package in `find src -name OpenSearch*.nupkg`
                do
                    dotnet nuget push \$package --api-key ${API_KEY} --source https://api.nuget.org/v3/index.json
                done
        """
    }
}
