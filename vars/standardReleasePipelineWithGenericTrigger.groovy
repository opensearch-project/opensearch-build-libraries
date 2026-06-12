/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** A standard release pipeline for OpenSearch projects including generic triggers. A tag or a pre-release can be used as a trigger using this library. The defaults are all set to trigger via a pre-release. If the release is successful, a GitHub issue is created for maintainers to publish the release.
@param Map arguments = [:] arguments A map of the following parameters
@param body <Required> - A closure containing release steps to be executed in release stage.
@param arguments.tokenIdCredential <Required> - Credential id containing token for trigger authentication
@param arguments.overrideAgent <Optional> - Jenkins agent label to override the default ('Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host')
@param arguments.overrideDockerImage <Optional> - Docker image to override the default ('opensearchstaging/ci-runner:release-centos7-clients-v1')
@param arguments.jsonValue <Optional> - Json value retrieved from payload of the webhook. Defaults to '$.release.tag_name'
@param arguments.causeString <Optional> - String mentioning why the workflow was triggered. Defaults to 'A tag was cut on GitHub repository causing this workflow to run'
@param arguments.regexpFilterText <Optional> - Variable to apply regular expression on. Defaults to '$isPreRelease $action'
@param arguments.regexpFilterExpression <Optional> - Regular expression to test on the evaluated text specified. Defaults to '^true published$'
@param arguments.downloadReleaseAsset <Optional> - If set to true, the assets attached to the release that triggered the job will be downloaded. Defaults to false.
@param arguments.downloadReleaseAssetName <Optional> - Name of the tar.gz file attached to the pre-release and containing artifacts to release. Defaults to 'artifacts.tar.gz'.
*/

void call(Map arguments = [:], Closure body) {
    def secret_github_bot = [
        [envVar: 'GITHUB_USER', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-username'],
        [envVar: 'GITHUB_TOKEN', secretRef: 'op://opensearch-release-secrets/github-bot/ci-bot-token']
    ]

    pipeline {
        agent
        {
            docker {
                label arguments.overrideAgent ?: 'Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host'
                image arguments.overrideDockerImage ?: 'opensearchstaging/ci-runner:release-centos7-clients-v4'
                args arguments.overrideDockerArgs ?: '-e JAVA_HOME=/opt/java/openjdk-11'
                alwaysPull true
            }
        }
        options {
            timeout(time: 1, unit: 'HOURS')
        }
        triggers {
            GenericTrigger(
                genericVariables: [
                    [key: 'ref', value: (arguments.jsonValue ?: '$.release.tag_name')],
                    [key: 'repository', value: '$.repository.html_url'],
                    [key: 'action', value: '$.action'],
                    [key: 'isPreRelease', value: '$.release.prerelease'],
                    [key: 'release_url', value: '$.release.url'],
                    [key: 'assets_url', value: '$.release.assets_url']
                ],
                tokenCredentialId: arguments.tokenIdCredential,
                causeString: arguments.causeString ?: 'A tag was cut on GitHub repository causing this workflow to run',
                printContributedVariables: false,
                printPostContent: false,
                regexpFilterText: (arguments.regexpFilterText ?: '$isPreRelease $action'),
                regexpFilterExpression: (arguments.regexpFilterExpression ?: '^true published$')
            )
        }
        environment {
            tag = "$ref"
            repository = "$repository"
        }
        stages {
            stage('Download artifacts') {
                when {
                    expression {
                        return arguments.downloadReleaseAsset
                    }
                }
                steps {
                    script {
                        if (arguments.downloadReleaseAsset && "$assets_url" != '') {
                        withSecrets(secrets: secret_github_bot){
                                def assetName = arguments.downloadReleaseAssetName ?: 'artifacts.tar.gz'
                                String assetUrl = null

                                String assets = sh(
                                    script: "curl -s --fail -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer ${GITHUB_TOKEN}' ${assets_url}",
                                    returnStdout: true
                                )
                                def parsedJson = readJSON text: assets
                                parsedJson.each { item ->
                                    if (item.name == assetName) {
                                        assetUrl = item.url
                                    }
                                }

                                if (assetUrl == null) {
                                    def availableAssets = parsedJson.collect { it.name }.join(', ')
                                    error("Asset '${assetName}' not found in release assets. Available assets: [${availableAssets}]. Assets URL: ${assets_url}")
                                }

                                echo "Downloading artifacts from ${assetUrl}"
                                sh "curl -s --fail -J -L -H 'Accept: application/octet-stream' -H 'Authorization: Bearer ${GITHUB_TOKEN}' ${assetUrl} -o artifacts.tar.gz && tar -xvf artifacts.tar.gz"
                            }
                        }
                    }
                }
            }
            stage('Release') {
                steps {
                    script {
                        body()
                    }
                }
            }
        }
        post {
            success {
                script {
                    withSecrets(secrets: secret_github_bot){
                        def codeOwners = sh(
                            script: "gh api repos/${repository.replaceAll('https://github.com/', '')}/contents/.github/CODEOWNERS --jq '.content' 2>/dev/null | tr -d '\\n' | base64 -d | grep -oP '@[\\w/-]+' | sort -u | tr '\\n' ' ' || echo ''",
                            returnStdout: true
                        ).trim()
                        def issueBody = "The release workflow for tag `${tag}` completed successfully.\n\nBuild: ${env.BUILD_URL}\n\nPlease publish the release on GitHub by converting the pre-release to a full release.\n\nRelease: ${repository}/releases/tag/${tag}\n\ncc: ${codeOwners}"
                        sh """gh issue create --title \"[Release - Action Required] Publish release for tag ${tag}\" --body \"${issueBody}\" --repo ${repository}"""
                    }
                }
            }
            failure {
                script {
                        withSecrets(secrets: secret_github_bot){
                        def codeOwners = sh(
                            script: "gh api repos/${repository.replaceAll('https://github.com/', '')}/contents/.github/CODEOWNERS --jq '.content' 2>/dev/null | tr -d '\\n' | base64 -d | grep -oP '@[\\w/-]+' | sort -u | tr '\\n' ' ' || echo ''",
                            returnStdout: true
                        ).trim()
                        def issueBody = "The release workflow for tag `${tag}` failed.\n\nPlease investigate the failure and retry the release.\n\nRelease: ${repository}/releases/tag/${tag}\n\ncc: ${codeOwners} @opensearch-project/engineering-effectiveness"
                        sh """gh issue create --title \"[RELEASE] Failed: release for tag ${tag}\" --body \"${issueBody}\" --repo ${repository}"""
                    }
                }
            }
            always {
                script {
                    publishNotification(
                        icon: currentBuild.currentResult == 'SUCCESS' ? ':white_check_mark:' : ':x:',
                        message: currentBuild.currentResult == 'SUCCESS' ? "Release ${tag} completed successfully" : "Release ${tag} failed",
                        extra: "REPOSITORY: ${repository}\nTAG: ${tag}",
                        credentialsId: 'opensearch-release-notification-webhook'
                    )
                    postCleanup()
                }
            }
        }
    }
}
