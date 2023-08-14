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
    boolean tag_latest = false

    if (args.tag == "2"){
        tag_latest = true
    }

    sh """#!/bin/bash
    set -e
    set +x
    docker pull ${docker_image}
    docker inspect --format '{{ index .Config.Labels "org.label-schema.version"}}' ${docker_image} > versionNumber
    docker inspect --format '{{ index .Config.Labels "org.label-schema.build-date"}}' ${docker_image} > time
    docker inspect --format '{{ index .Config.Labels "org.label-schema.description"}}' ${docker_image} > buildNumber"""

    version = readFile('versionNumber').trim()
    build_time = readFile('time').trim()
    build_number = readFile('buildNumber').trim()

    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: "manifests/${version}/${args.product}-${version}.yml"))

    artifactUrlX64 = "https://ci.opensearch.org/ci/dbc/distribution-build-${args.product}/${version}/${build_number}/linux/x64/tar/dist/${args.product}/${args.product}-${version}-linux-x64.tar.gz"

    artifactUrlARM64 = "https://ci.opensearch.org/ci/dbc/distribution-build-${args.product}/${version}/${build_number}/linux/arm64/tar/dist/${args.product}/${args.product}-${version}-linux-arm64.tar.gz"

    //slice the build-date value (For Example: 2023-08-11T02:17:43Z -> 20230811)
    build_date = build_time[0..3] + build_time[5..6] + build_time[8..9]

    def build_qualifier = inputManifest.build.qualifier

    if (build_qualifier != null && build_qualifier != 'null') {
        build_qualifier = "-" + build_qualifier
    }
    else {
        build_qualifier = ''
    }

    buildDockerImage(
        inputManifest: "manifests/${version}/${args.product}-${version}.yml",
        buildNumber: "${build_number}",
        buildDate: "${build_date}",
        buildOption: "${args.rerelease}",
        artifactUrlX64: "${artifactUrlX64}",
        artifactUrlArm64: "${artifactUrlARM64}"
    )

    echo 'Triggering docker-promote'
    if(args.rerelease == "re_release_docker_image"){
        dockerPromote: {
            build job: 'docker-promote',
            parameters: [
                string(name: 'SOURCE_IMAGES', value: "${args.product}:${inputManifest.build.version}${build_qualifier}.${build_number}.${build_date}"),
                string(name: 'RELEASE_VERSION', value: "${version}"),
                booleanParam(name: 'TAG_LATEST', value: "${tag_latest}")
            ]
        }
    }
}
