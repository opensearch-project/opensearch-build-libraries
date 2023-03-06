/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/**
SignArtifacts signs the given artifacts and saves the signature in the same directory.
@param Map[version] <Required> - Version of the opensearch artifact that needs to be validated.
@param Map[distribution] <Optional> - Choose distribution type among [tar(default),rpm,yum,docker].
@param Map[architecture] <Optional> - Select the architecture type among [x64(default) and arm64].
@param Map[platform] <Optional> - The distribution platform.
@param Map[docker_source] <Optional> - Specify the docker source from [DockerHub(default), ECR] to pull the docker image.
@param Map[os_build_number]<Optional> - Specify Opensearch build number from opensearchstaging if required.
@param Map[osd_build_number]<Optional> - Specify Opensearch-Dashboard build number from opensearchstaging if required.
*/
void call(Map args = [:]) {
        String workdir = "${WORKSPACE}"
        echo 'Validating artifacts'

        if (!fileExists("$WORKSPACE/validation.sh")) {
            dir('opensearch-build') {
                git url: 'https://github.com/opensearch-build.git', branch: 'main'
                workdir = "${WORKSPACE}/opensearch-build"
            }
        }

        String arguments = generateArguments(args)

        sh """
                   ${workdir}/validation.sh ${arguments}
               """
}

String generateArguments(args) {
    String arguments = ""

    // generation of command line arguments
    args.each { key, value -> arguments += " --${key } ${value }" }
    return arguments
}
