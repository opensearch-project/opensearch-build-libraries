/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to sign and deploy opensearch artifacts to a sonatype staging repository.
 @param Map args = [:] args A map of the following parameters
 @param args.signingArtifactsPath <required> - Local Path to yml or artifact file. This could be path to Dir that contains artifacts or full path to yml file.
 @param args.mavenArtifactsPath <required> - The local directory containing distribution files to upload to the repository. example: ~/.m2/repository where repository contains /org/opensearch
 @param args.autoPublish <optional> - Set it to true to auto-release maven artifacts from staging repo to production. Default is false.
 */

void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@1.6.0', retriever: legacySCM(scm))
    loadCustomScript(scriptPath: 'publish/stage-maven-release.sh',
                     scriptName: 'stage-maven-release.sh')
    def autoPublish = args.autoPublish ?: false
    println("Signing Maven artifacts.")
    signArtifacts(
            artifactPath: args.signingArtifactsPath,
            type: 'maven',
            platform: 'linux',
            sigtype: '.asc'
    )

    println("Stage and Release Maven artifacts.")
    withCredentials([usernamePassword(credentialsId: 'jenkins-sonatype-creds', usernameVariable: 'SONATYPE_USERNAME', passwordVariable: 'SONATYPE_PASSWORD')]) {
        sh("./stage-maven-release.sh ${args.mavenArtifactsPath} ${autoPublish}")
    }
}
