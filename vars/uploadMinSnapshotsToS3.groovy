/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@6.4.4', retriever: legacySCM(scm))
    List<Closure> fileActions = args.fileActions ?: []
    String manifest = args.manifest ?: "manifests/${INPUT_MANIFEST}"
    String distribution_arg = args.distribution ?: 'None'

    if (distribution_arg == 'None') {
        echo("Missing distribution type")
        System.exit(1)
    }

    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: manifest))
    String productName = inputManifest.build.getFilename()
    echo("Retreving build manifest from: $WORKSPACE/${distribution_arg}/builds/${productName}/manifest.yml")

    def buildManifest = lib.jenkins.BuildManifest.new(readYaml(file: "$WORKSPACE/${distribution_arg}/builds/${productName}/manifest.yml"))
    String version = buildManifest.build.version
    String architecture = buildManifest.build.architecture
    String platform = buildManifest.build.platform
    String id = buildManifest.build.id
    String distribution = buildManifest.build.distribution
    String extension = buildManifest.build.getExtension()

    if (distribution_arg != distribution) {
        echo("User entered $distribution_arg does not match the distribution specified in the build manifest $distribution")
        System.exit(1)
    }

    // Setup src & dst variables for artifacts
    // Replace backslash with forward slash ('\' to '/') in path
    // Compatible with both Windows as well as any nix* env
    // Else the map in groovy will treat '\' as escape char on Windows
    String srcDir = "${WORKSPACE}/${distribution}/builds/${productName}/dist".replace("\\", "/")
    String dstDir = "snapshots/core/${productName}/${version}"
    String baseName = "${productName}-min-${version}-${platform}-${architecture}"

    // Create checksums
    echo('Create .sha512 for Min Snapshots Artifacts')
    argsMap = [:]
    argsMap['artifactPath'] = srcDir
    for (Closure action : fileActions) { // running createSha512Checksums()
        action(argsMap)
    }

    echo("Start copying files: version-${version} architecture-${architecture} platform-${platform} buildid-${id} distribution-${distribution} extension-${extension}")

    sh """
        cp -v ${srcDir}/${baseName}.${extension} ${srcDir}/${baseName}-latest.${extension}
        cp -v ${srcDir}/${baseName}.${extension}.sha512 ${srcDir}/${baseName}-latest.${extension}.sha512
        cp -v ${srcDir}/../manifest.yml ${srcDir}/${baseName}-latest.${extension}.build-manifest.yml
        sed -i "s/.${extension}/-latest.${extension}/g" ${srcDir}/${baseName}-latest.${extension}.sha512
    """
    withCredentials([
        string(credentialsId: 'jenkins-artifact-promotion-role', variable: 'ARTIFACT_PROMOTION_ROLE_NAME'),
        string(credentialsId: 'jenkins-aws-production-account', variable: 'AWS_ACCOUNT_ARTIFACT'),
        string(credentialsId: 'jenkins-artifact-production-bucket-name', variable: 'ARTIFACT_PRODUCTION_BUCKET_NAME')]) {
            withAWS(role: "${ARTIFACT_PROMOTION_ROLE_NAME}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                s3Upload(file: "${srcDir}/${baseName}-latest.${extension}", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${dstDir}/${baseName}-latest.${extension}")
                s3Upload(file: "${srcDir}/${baseName}-latest.${extension}.sha512", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${dstDir}/${baseName}-latest.${extension}.sha512")
                s3Upload(file: "${srcDir}/${baseName}-latest.${extension}.build-manifest.yml", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${dstDir}/${baseName}-latest.${extension}.build-manifest.yml")
            }
        }
}
