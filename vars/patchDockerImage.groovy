/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    String docker_image = "opensearchproject/${args.product}:${args.tag}"

    sh"""
    #!/bin/bash
    set -e
    set +x

    docker pull ${docker_image}
    """
    sh """docker inspect --format '{{ index .Config.Labels "org.label-schema.version"}}' ${docker_image} > versionnumber"""

    sh """docker inspect --format '{{ index .Config.Labels "org.label-schema.build-date"}}' ${docker_image} > time"""

    sh """docker inspect --format '{{ index .Config.Labels "org.label-schema.description"}}' ${docker_image} > number"""

    version = readFile('versionnumber').trim()
    build_time = readFile('time').trim()
    build_number = readFile('number').trim()

    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: "manifests/${version}/${args.product}-${version}.yml"))

    artifactUrlX64 = "https://ci.opensearch.org/ci/dbc/distribution-build-${args.product}/${version}/${build_number}/linux/x64/tar/dist/${args.product}/${args.product}-${version}-linux-x64.tar.gz"

    artifactUrlARM64 = "https://ci.opensearch.org/ci/dbc/distribution-build-${args.product}/${version}/${build_number}/linux/arm64/tar/dist/${args.product}/${args.product}-${version}-linux-arm64.tar.gz"

    /*slice the time to get date value*/
    build_date = build_time[0..3] + build_time[5..6] + build_time[8..9]

    def build_qualifier = inputManifest.build.qualifier

    if (build_qualifier != null && build_qualifier != 'null') {
        build_qualifier = "-" + build_qualifier
    }
    else {
        build_qualifier = ''
    }

    if (artifactUrlX64 == null || artifactUrlARM64 ==  null) {
    echo 'Skipping docker build, one of x64 or arm64 artifacts was not built.'
    } else {
        echo 'Trigger docker-build'
        dockerBuild: {
            build job: 'docker-build',
            parameters: [
                string(name: 'DOCKER_BUILD_GIT_REPOSITORY', value: 'https://github.com/opensearch-project/opensearch-build'),
                string(name: 'DOCKER_BUILD_GIT_REPOSITORY_REFERENCE', value: 'main'),
                string(name: 'DOCKER_BUILD_SCRIPT_WITH_COMMANDS', value: [
                        'id',
                        'pwd',
                        'cd docker/release',
                        "curl -sSL ${artifactUrlX64} -o ${args.product}-x64.tgz",
                        "curl -sSL ${artifactUrlARM64} -o ${args.product}-arm64.tgz",
                        [
                            'bash',
                            'build-image-multi-arch.sh',
                            "-v ${inputManifest.build.version}${build_qualifier}",
                            "-f ./dockerfiles/${args.product}.al2.dockerfile",
                            "-p ${args.product}",
                            "-a 'x64,arm64'",
                            "-r opensearchstaging/${args.product}",
                            "-t '${args.product}-x64.tgz,${args.product}-arm64.tgz'",
                            "-n ${build_number}"
                        ].join(' ')
                ].join(' && ')),
            ]
        }

        echo 'Trigger docker-copy with tag build date '
        if (args.rerelease) {
            dockerCopy: {
                build job: 'docker-copy',
                parameters: [
                    string(name: 'SOURCE_IMAGE_REGISTRY', value: 'opensearchstaging'),
                    string(name: 'SOURCE_IMAGE', value: "${args.product}:${inputManifest.build.version}${build_qualifier}"),
                    string(name: 'DESTINATION_IMAGE_REGISTRY', value: 'opensearchstaging'),
                    string(name: 'DESTINATION_IMAGE', value: "${args.product}:${inputManifest.build.version}${build_qualifier}.${build_number}.${build_date}")
                ]
            }
        }

        echo "Trigger docker-scan for ${args.product} version ${inputManifest.build.version}${build_qualifier}"
        dockerScan: {
            build job: 'docker-scan',
            parameters: [
                string(name: 'IMAGE_FULL_NAME', value: "opensearchstaging/${args.product}:${inputManifest.build.version}${build_qualifier}")
            ]
        }

        echo 'Trigger docker-promote'
        if(args.rerelease){
            dockerPromote: {
                build job: 'docker-promote',
                parameters: [
                    string(name: 'SOURCE_IMAGES', value: "${args.product}:${inputManifest.build.version}${build_qualifier}.${build_number}.${build_date}"),
                    string(name: 'RELEASE_VERSION', value: "${version}")
                ]
            }
        }
    }
}
