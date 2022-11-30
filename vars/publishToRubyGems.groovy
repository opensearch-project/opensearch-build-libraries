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
@param args.apiKeyCredentialId <required> - Credential id consisting api key for publishing the gem to rubyGems.org
@param args.gemsDir <optional> - The directory containing the gem to be published. Defaults to 'dist'
@params args.publicCertPath <optional> - The relative path to public key. Defaults to 'certs/opensearch-rubygems.pem'
*/

import java.util.logging.Logger;

log = Logger.getLogger("InfoLogging");

void call(Map args = [:]) {
    String releaseArtifactsDir = args.gemsDir ? "${WORKSPACE}/${args.gemsDir}" : "${WORKSPACE}/dist"
    String certPath = args.publicCertPath ? "${WORKSPACE}/${args.publicCertPath}" : "${WORKSPACE}/certs/opensearch-rubygems.pem"

    sh "gem cert --add ${certPath} && cd ${releaseArtifactsDir}"
    log.info('Verifying the gem signature')
    def gemNameWithVersion = sh 'ls *.gem'
    sh """
        gem install '${gemNameWithVersion}';
        gemName=$(echo ${gemNameWithVersion} | sed -E 's/(-[0-9.]+.gem$)//g');
        gem uninstall ${gemName};
        gem install ${gemNameWithVersion} -P HighSecurity;
    """

    log.info('Publishing the gem')
    withCredentials([string(credentialsId: "${args.apiKeyCredentialId}", variable: 'API_KEY')]) {
        sh "curl --fail --data-binary @${gemName}` -H 'Authorization:${API_KEY}' -H 'Content-Type: application/octet-stream' https://rubygems.org/api/v1/gems"
        }
}
