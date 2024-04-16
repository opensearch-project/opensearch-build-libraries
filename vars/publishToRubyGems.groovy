/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to publish ruby gems to rubygems.org registry with OpenSearch as the owner.
Note: Please make sure the gem is already signed. For ruby versions, they to be preinstalled as a part of docker. See the default docker file: https://github.com/opensearch-project/opensearch-build/blob/main/docker/ci/dockerfiles/current/release.centos.clients.x64.arm64.dockerfile
@param Map args = [:] args A map of the following parameters
@param args.apiKeyCredentialId <required> - Credential id consisting api key for publishing the gem to rubyGems.org
@param args.gemsDir <optional> - The directory containing the gem to be published. Defaults to 'dist'
@params args.publicCertPath <optional> - The relative path to public key. Defaults to 'certs/opensearch-rubygems.pem'
@params args.rubyVersion <optional> - Ruby version to be used. Defaults to 2.6.0
*/


void call(Map args = [:]) {
    String releaseArtifactsDir = args.gemsDir ? "${WORKSPACE}/${args.gemsDir}" : "${WORKSPACE}/dist"
    String certPath = args.publicCertPath ? "${WORKSPACE}/${args.publicCertPath}" : "${WORKSPACE}/certs/opensearch-rubygems.pem"
    String rubyVersion = args.rubyVersion ?: '2.6.0'

    sh """#!/bin/bash
        source ~/.rvm/scripts/rvm && rvm use ${rubyVersion} && ruby --version
        gem cert --add ${certPath}
        cd ${releaseArtifactsDir} && gemNameWithVersion=\$(ls *.gem)
        gem install \$gemNameWithVersion
        gemName=\$(echo \$gemNameWithVersion | sed -E 's/(-[0-9.]+-*[a-z]*.gem\$)//g')
        gem uninstall \$gemName
        gem install \$gemNameWithVersion -P MediumSecurity
        gem uninstall \$gemName
        gem install \$gemNameWithVersion -P HighSecurity
    """

    withCredentials([string(credentialsId: "${args.apiKeyCredentialId}", variable: 'API_KEY')]) {
        sh "cd ${releaseArtifactsDir} && curl --fail --data-binary @`ls *.gem` -H 'Authorization:${API_KEY}' -H 'Content-Type: application/octet-stream' https://rubygems.org/api/v1/gems"
    }
}
