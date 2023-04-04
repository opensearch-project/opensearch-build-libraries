/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** A standard release pipeline for OpenSearch projects including generic triggers. A tag or a draft release can be used as a trigger using this library. The defaults are all set to trigger via a draft release. If the release is successful, the release can be published by using right params.
@param Map arguments = [:] arguments A map of the following parameters
@param body <Required> - A closure containing release steps to be executed in release stage.
@param arguments.tokenIdCredential <Required> - Credential id containing token for trigger authentication
@param arguments.overrideAgent <Optional> - Jenkins agent label to override the default ('Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host')
@param arguments.overrideDockerImage <Optional> - Docker image to override the default ('opensearchstaging/ci-runner:release-centos7-clients-v1')
@param arguments.jsonValue <Optional> - Json value retrieved from payload of the webhook. Defaults to '$.release.tag_name'
@param arguments.causeString <Optional> - String mentioning why the workflow was triggered. Defaults to 'A tag was cut on GitHub repository causing this workflow to run'
@param arguments.regexpFilterText <Optional> - Variable to apply regular expression on. Defaults to '$isDraft'
@param arguments.regexpFilterExpression <Optional> - Regular expression to test on the evaluated text specified. Defaults to ''
@param arguments.publishRelease <Optional> - If set to true the release that triggered the job will be published on GitHub.
@param arguments.downloadReleaseAsset <Optional> - If set to true, the assets attached to the release that triggered the job will be downloaded. Defaults to false.
@param arguments.downloadReleaseAssetName <Optional> - Name of the tar.gz file attached to the draft release and containing artifacts to release. Defaults to 'artifacts.tar.gz'.
*/

void call(Map arguments = [:], Closure body) {
    pipeline {
        agent
        {
            docker {
                label arguments.overrideAgent ?: 'Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host'
                image arguments.overrideDockerImage ?: 'opensearchstaging/ci-runner:release-centos7-clients-v1'
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
                    [key: 'isDraft', value: '$.release.draft'],
                    [key: 'release_url', value: '$.release.url'],
                    [key: 'assets_url', value: '$.release.assets_url']
                ],
                tokenCredentialId: arguments.tokenIdCredential,
                causeString: arguments.causeString ?: 'A tag was cut on GitHub repository causing this workflow to run',
                printContributedVariables: false,
                printPostContent: false,
                regexpFilterText: (arguments.regexpFilterText ?: '$isDraft $action'),
                regexpFilterExpression: (arguments.regexpFilterExpression ?: '^true created$')
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
                            withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN')]) {
                                String assets = sh(
                                    script: "curl -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer ${GITHUB_TOKEN}' ${assets_url}",
                                    returnStdout: true
                                )
                                String assetUrl = null
                                def parsedJson = readJSON text: assets
                                def assetName = arguments.downloadReleaseAssetName ?: 'artifacts.tar.gz'
                                parsedJson.each { item ->
                                    if(item.name == assetName) {
                                        assetUrl = item.url
                                        }
                                    }
                                echo "Downloading artifacts from $assetUrl"
                                sh "curl -J -L -H 'Accept: application/octet-stream' -H 'Authorization: Bearer ${GITHUB_TOKEN}' ${assetUrl} -o artifacts.tar.gz && tar -xvf artifacts.tar.gz"
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
                    if (arguments.publishRelease && release_url != null) {
                        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN')]) {
                            sh "curl -X PATCH -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer ${GITHUB_TOKEN}' ${release_url} -d '{\"tag_name\":\"${tag}\",\"draft\":false,\"prerelease\":false}'"
                        }
                    }
                }
            }
            always {
                script {
                    postCleanup()
                }
            }
        }
    }
}
