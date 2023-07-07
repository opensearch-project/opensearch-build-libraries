/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** A standard release pipeline for OpenSearch projects
@param Map args = [:] args A map of the following parameters
@param body <Required> - A closure containing release steps to be executed in release stage.
@param args.overrideAgent <Optional> - Jenkins agent label to override the default.
@param args.overrideDockerImage <Optional> - Docker image to override the default.
*/

void call(Map args = [:], Closure body) {
    pipeline {
        agent
        {
            docker {
                label args.overrideAgent ?: 'Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host'
                image args.overrideDockerImage ?: 'opensearchstaging/ci-runner:release-centos7-clients-v4'
                alwaysPull true
            }
        }
        options {
            timeout(time: 1, unit: 'HOURS')
        }
        stages{
            stage("Release") {
                steps {
                    script {
                        body()
                    }
                }
            }
        }
        post {
            always {
                script {
                    postCleanup()
                    }
                }
            }
        }
    }