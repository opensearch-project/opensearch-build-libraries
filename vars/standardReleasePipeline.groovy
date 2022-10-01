/** A standard release pipeline for OpenSearch projects
@param Map args = [:] args A map of the following parameters
@param args.overrideAgent <Optional> - Jenkins agent label to override the default.
@param args.overrideDockerImage <Optional> - Docker image to override the default.
@param body <Required> - A closure containing release steps to be executed in release stage.
*/

void call(Map args = [:], Closure body) {
    pipeline {
        agent
        {
            docker {
                label args.overrideAgent ?: 'Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host'
                image args.overrideDockerImage ?: 'opensearchstaging/ci-runner:ci-runner-centos7-opensearch-build-v2'
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
                    sh 'docker image prune -f --all'
                    }
                }
            }
        }
    }