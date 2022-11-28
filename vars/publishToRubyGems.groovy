/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to publish ruby gems to rubygems.org registry with OpenSearch as the owner.
Note: Please make sure the gem is already signed.
@param Map args = [:] args A map of the following parameters
@param args.apiKey <required> - Credential id consisting api key for publishing the gem to rubyGems.org
@param args.gemsDir <optional> - The directory containing gems to be published. Defaults to 'dist'
@params args.publicCertPath <optional> - The relative path to public key. Defaults to 'cert/opensearch-rubygems.pem'
*/
void call(Map args = [:]) {
    String releaseArtifactsDir = args.gemsDir ? "${WORKSPACE}/${args.gemsDir}" : "${WORKSPACE}/dist"
    String certPath = args.publicCertPath ? "${WORKSPACE}/${args.publicCertPath}" : "${WORKSPACE}/cert/opensearch-rubygems.pem"

    withCredentials([string(credentialsId: "${args.apiKey}", variable: 'API_KEY')]) {
        sh """gem cert --add ${certPath} && \
            cd ${releaseArtifactsDir} && gem install `ls *.gem` -P HighSecurity && \
            curl --fail --data-binary @`ls *.gem` -H 'Authorization:${API_KEY}' -H 'Content-Type: application/octet-stream' https://rubygems.org/api/v1/gems"""
        }
}
