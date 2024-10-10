/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/**
Library to support Docker Image Re-Release Automation
@param Map[product] <Required> - Product type refers to opensearch or opensearch-dashboards.
@param Map[tag] <Required> - Tag of the product that needs to be re-released.
*/
void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@7.0.1', retriever: legacySCM(scm))
    String docker_image = "opensearchproject/${args.product}:${args.tag}"
    String latest_docker_image = "opensearchproject/${args.product}:latest"
    boolean tag_latest = false
    String build_option = "re_release_docker_image"

    sh """#!/bin/bash
    set -e
    set +x
    docker pull ${docker_image}
    docker pull ${latest_docker_image}
    """

    def version = sh (
            script: """docker inspect --format '{{ index .Config.Labels "org.label-schema.version"}}' ${docker_image}""",
            returnStdout: true
    ).trim()
    def build_date = sh (
            script: """date +%Y%m%d""",
            returnStdout: true
    ).trim()
    def build_number = sh (
            script: """docker inspect --format '{{ index .Config.Labels "org.label-schema.description"}}' ${docker_image}""",
            returnStdout: true
    ).trim()
    def latest_version = sh (
            script: """docker inspect --format '{{ index .Config.Labels "org.label-schema.version"}}' ${latest_docker_image}""",
            returnStdout: true
    ).trim()

    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: "manifests/${version}/${args.product}-${version}.yml"))

    artifactUrlX64 = "https://ci.opensearch.org/ci/dbc/distribution-build-${args.product}/${version}/${build_number}/linux/x64/tar/dist/${args.product}/${args.product}-${version}-linux-x64.tar.gz"

    artifactUrlARM64 = "https://ci.opensearch.org/ci/dbc/distribution-build-${args.product}/${version}/${build_number}/linux/arm64/tar/dist/${args.product}/${args.product}-${version}-linux-arm64.tar.gz"

    def build_qualifier = inputManifest.build.qualifier

    if (build_qualifier != null && build_qualifier != 'null') {
        build_qualifier = "-" + build_qualifier
    }
    else {
        build_qualifier = ''
    }

    if (latest_version == version){
        tag_latest = true
    }

    buildDockerImage(
        inputManifest: "manifests/${version}/${args.product}-${version}.yml",
        buildNumber: "${build_number}",
        buildGitRef: "${version}",
        buildDate: "${build_date}",
        buildOption: "${build_option}",
        artifactUrlX64: "${artifactUrlX64}",
        artifactUrlArm64: "${artifactUrlARM64}"
    )

    echo 'Triggering docker-promotion'
    dockerPromote: {
        build job: 'docker-promotion',
        propagate: true,
        wait: true,
        parameters: [
            string(name: 'SOURCE_IMAGES', value: "${args.product}:${inputManifest.build.version}${build_qualifier}.${build_number}.${build_date}"),
            string(name: 'RELEASE_VERSION', value: "${version}"),
            booleanParam(name: 'TAG_LATEST', value: "${tag_latest}")
        ]
    }
}
