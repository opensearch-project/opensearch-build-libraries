/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/**
Wrapper that runs validation.sh script with provided args.
[
@param Map[version] <Required> - Version of the opensearch artifact that needs to be validated.
@param Map[file_path] <Required> - Url paths to Opensearch or OpenSearch DashBoards artifacts.
Note: These parameters are mutually exclusive. Provide either of 'version' or  'file_path' parameters, to proceed with the validation.
]
@param Map[distribution] <Optional> - Choose distribution type among [tar(default),rpm,yum,docker].
@param Map[architecture] <Optional> - Select the architecture type among [x64(default) and arm64].
@param Map[platform] <Optional> - The distribution platform.
@param Map[projects] <Optional> - Specify the project type OpenSearch or Both(OpenSearch and OpenSearch-DashBoards).
@param Map[docker_source] <Optional> - Specify the docker source from [DockerHub(default), ECR] to pull the docker image.
@param Map[os_build_number]<Optional> - Specify Opensearch build number from opensearchstaging if required.
@param Map[osd_build_number]<Optional> - Specify Opensearch-Dashboard build number from opensearchstaging if required.
@param Map[artifact_type] <Optional> - Select the artifact type among [staging and production].
@param Map[allow_http] <Optional> - Supports validation of artifacts in which security plugin is absent.architecture.
@param Map[docker_args] <Optional> - Select either of [using-staging-artifact-only', 'validate-digest-only] for docker validation.
*/
void call(Map args = [:]) {
    if (!fileExists("$WORKSPACE/validation.sh")) {
        println("Validation.sh script not found in the workspace: ${WORKSPACE}, exit 1")
        System.exit(1)
    }

    sh([
        './validation.sh',
        args.version ? "--version ${args.version}" : "",
        args.file_path ? "--file-path ${args.file_path}" : "",
        args.distribution ? "--distribution ${args.distribution}" : "",
        args.platform ? "--platform ${args.platform}" : "",
        args.arch ? "--arch ${args.arch}" : "",
        args.projects ? "--projects ${args.projects}" : "",
        args.docker_source ? "--docker-source ${args.docker_source}" : "",
        args.os_build_number ? "--os-build-number ${args.os_build_number}" : "",
        args.osd_build_number ? "--osd-build-number ${args.osd_build_number}" : "",
        args.artifact_type ? "--artifact-type ${args.artifact_type}" : "",
        args.allow_http ? '--allow-http' : "",
        args.docker_args ? "--${args.docker_args}" : "",
    ].join(' ').trim())
}
