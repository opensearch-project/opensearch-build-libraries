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
    List<Closure> fileActions = args.fileActions ?: []
    String manifest = args.manifest ?: "manifests/${INPUT_MANIFEST}"
    String distribution_arg = args.distribution ?: 'None'

    if (distribution_arg == 'None') {
        echo("Missing distribution type")
        System.exit(1)
    }

    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: manifest))
    String productName = inputManifest.build.getFilename()
    String version_plain = inputManifest.build.version
    String qualifier = inputManifest.build.qualifier ? '-' + inputManifest.build.qualifier : ''
    String revision = version_plain + qualifier + '-SNAPSHOT'
    def buildManifestYamlOnly = readYaml(file: "$WORKSPACE/${distribution_arg}/builds/${productName}/manifest.yml")
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

    withCredentials([
        string(credentialsId: 'jenkins-artifact-promotion-role', variable: 'ARTIFACT_PROMOTION_ROLE_NAME'),
        string(credentialsId: 'jenkins-aws-production-account', variable: 'AWS_ACCOUNT_ARTIFACT'),
        string(credentialsId: 'jenkins-artifact-production-bucket-name', variable: 'ARTIFACT_PRODUCTION_BUCKET_NAME')]) {

            // Setup core plugins snapshots with .sha512 and .sig (Tar x64 only)
            String corePluginDir = "${WORKSPACE}/${distribution}/builds/${productName}/core-plugins".replace("\\", "/")
            boolean corePluginDirExists = fileExists(corePluginDir)
            if (architecture == "x64" && platform == "linux" && distribution == "tar" && corePluginDirExists) {
                echo("Create .sha512 and .sig for Core Plugins Snapshots")
                fileActions = [createSha512Checksums(), createSignatureFiles()]
                argsMapPlugins = [:]
                argsMapPlugins['sigtype'] = '.sig'
                argsMapPlugins['artifactPath'] = "${WORKSPACE}/${distribution}/builds/${productName}/core-plugins"
                for (Closure action : fileActions) {
                    action(argsMapPlugins)
                }
            }

            // Setup min snapshots with .sha512 (All distributions)
            echo('Create .sha512 for Min Snapshots Artifacts')
            fileActions = [createSha512Checksums()]
            argsMapMin = [:]
            argsMapMin['artifactPath'] = srcDir
            for (Closure action : fileActions) {
                action(argsMapMin)
            }

            echo("Start copying files: version-${version} revision-${revision} architecture-${architecture} platform-${platform} buildid-${id} distribution-${distribution} extension-${extension}")

            String sedCmd = "sed"
            if (platform == "darwin") {
                sedCmd = "gsed"
            }

            sh """
                cp -v ${srcDir}/${baseName}.${extension} ${srcDir}/${baseName}-latest.${extension}
                cp -v ${srcDir}/${baseName}.${extension}.sha512 ${srcDir}/${baseName}-latest.${extension}.sha512
                cp -v ${srcDir}/../manifest.yml ${srcDir}/${baseName}-latest.${extension}.build-manifest.yml
                ${sedCmd} -i "s/.${extension}/-latest.${extension}/g" ${srcDir}/${baseName}-latest.${extension}.sha512
            """
            withAWS(role: "${ARTIFACT_PROMOTION_ROLE_NAME}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                // min artifacts
                echo("Upload min snapshots")
                s3Upload(file: "${srcDir}/${baseName}-latest.${extension}", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${dstDir}/${baseName}-latest.${extension}")
                s3Upload(file: "${srcDir}/${baseName}-latest.${extension}.sha512", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${dstDir}/${baseName}-latest.${extension}.sha512")
                s3Upload(file: "${srcDir}/${baseName}-latest.${extension}.build-manifest.yml", bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}", path: "${dstDir}/${baseName}-latest.${extension}.build-manifest.yml")
                // core plugins
                if (architecture == "x64" && platform == "linux" && distribution == "tar" && corePluginDirExists) {
                    echo("Upload core-plugins snapshots")
                    List<String> corePluginList = buildManifestYamlOnly.components.artifacts.'core-plugins'[0]
                    echo("corePluginList: ${corePluginList}")
                    for (String pluginSubPath : corePluginList) {
                        String pluginSubFolder = pluginSubPath.split('/')[0]
                        String pluginNameWithExt = pluginSubPath.split('/')[1]
                        String pluginName = pluginNameWithExt.replace('-' + revision + '.zip', '')
                        String pluginFullPath = ['plugins', pluginName, revision].join('/')
                        s3Upload(
                            bucket: "${ARTIFACT_PRODUCTION_BUCKET_NAME}",
                            path: "snapshots/${pluginFullPath}/",
                            workingDir: "${corePluginDir}/",
                            includePathPattern: "**/${pluginName}*"
                        )
                    }
                }
            }
        }
}
