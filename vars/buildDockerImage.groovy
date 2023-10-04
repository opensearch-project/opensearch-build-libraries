/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/**
Library to build Docker Image with different Build Options
@param Map[inputManifest] <Required> - Path to Input Manifest.
@param Map[buildNumber] <Required> - Build number of the corresponding Artifact.
@param Map[buildDate] <Optional> - Date on which the artifacts were built.
@param Map[artifactUrlX64] <Required> - Url Path to X64 Tarball.
@param Map[artifactUrlARM64] <Required> - Url Path to ARM64 Tarball.
@param Map[buildOption] <Required> - Build Option for building the image with different options.
*/
void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@5.9.2', retriever: legacySCM(scm))
    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: args.inputManifest))
    def build_version = inputManifest.build.version
    def build_qualifier = inputManifest.build.qualifier
    def build_number = args.buildNumber ?: "${BUILD_NUMBER}"
    String image_tag = ""
    String image_base_os = "al2023"

    // Keep al2 for the 1.x versions
    if (build_version.split("\\.")[0] == "1") {
        image_base_os = "al2"
    }

    if (args.buildDate != null && args.buildDate != 'null'){
        image_tag = "." + "${args.buildDate}"
    }

    if (build_qualifier != null && build_qualifier != 'null') {
        build_qualifier = "-" + build_qualifier
    }
    else {
        build_qualifier = ''
    }
    String filename = inputManifest.build.getFilename()

    if (args.artifactUrlX64 == null || args.artifactUrlArm64 ==  null) {
        echo 'Skipping docker build, one of x64 or arm64 artifacts was not built.'
    } else {
        echo 'Triggering docker-build'
        dockerBuild: {
            build job: 'docker-build',
            propagate: true,
            wait: true,
            parameters: [
                string(name: 'DOCKER_BUILD_GIT_REPOSITORY', value: 'https://github.com/opensearch-project/opensearch-build'),
                string(name: 'DOCKER_BUILD_GIT_REPOSITORY_REFERENCE', value: 'main'),
                string(name: 'DOCKER_BUILD_SCRIPT_WITH_COMMANDS', value: [
                        'id',
                        'pwd',
                        'cd docker/release',
                        "curl -sSL ${args.artifactUrlX64} -o ${filename}-x64.tgz",
                        "curl -sSL ${args.artifactUrlArm64} -o ${filename}-arm64.tgz",
                        [
                            'bash',
                            'build-image-multi-arch.sh',
                            "-v ${build_version}${build_qualifier}",
                            "-f ./dockerfiles/${filename}.${image_base_os}.dockerfile",
                            "-p ${filename}",
                            "-a 'x64,arm64'",
                            "-r opensearchstaging/${filename}",
                            "-t '${filename}-x64.tgz,${filename}-arm64.tgz'",
                            "-n ${build_number}"
                        ].join(' ')
                    ].join(' && ')),
            ]
        }

        echo 'Triggering docker create tag with build number'
        if (args.buildOption == "build_docker_with_build_number_tag" || args.buildOption == "re_release_docker_image") {
            dockerCopy: {
                build job: 'docker-copy',
                propagate: true,
                wait: true,
                parameters: [
                    string(name: 'SOURCE_IMAGE_REGISTRY', value: 'opensearchstaging'),
                    string(name: 'SOURCE_IMAGE', value: "${filename}:${build_version}${build_qualifier}"),
                    string(name: 'DESTINATION_IMAGE_REGISTRY', value: 'opensearchstaging'),
                    string(name: 'DESTINATION_IMAGE', value: "${filename}:${build_version}${build_qualifier}.${build_number}${image_tag}")
                ]
            }
        }

        echo "Triggering docker-scan for ${filename} version ${build_version}${build_qualifier}"
        dockerScan: {
            build job: 'docker-scan',
            propagate: true,
            wait: true,
            parameters: [
                string(name: 'IMAGE_FULL_NAME', value: "opensearchstaging/${filename}:${build_version}${build_qualifier}")
            ]
        }

    }
}
