/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /** Library to call build.sh script (builds OpenSearch and OpenSearch Dashboards distribution)
 @param Map args = [:] args A map of the following parameters
 @param args.inputManifest <required> - Relative path to input manifest containing all the components to build.
 @param args.distribution <optional> - Type of distribution to build. Defaults to null.
 @param args.componentName <optional> - Name of the single component to build. Defaults to null and builds all the components in the manifest.
 @param args.platform <optional> - Platform to build. Defaults to null.
 @param args.architecture <optional> - Architecture to build. Defaults to null.
 @param args.snapshot <optional> - Boolean value. Defaults to null.
 @param args.lock <optional> - Generate a stable reference manifest. Defaults to null.
 @param args.continueOnError <optional> - Do not fail the distribution build on any plugin component failure. Defaults to null
 @param args.incremental <optional> - Boolean value to enable incremental build.
 */
void call(Map args = [:]) {
    boolean incremental_enabled = args.incremental != null && args.incremental

    def lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))
    def inputManifestObj = lib.jenkins.InputManifest.new(readYaml(file: args.inputManifest))

    def DISTRIBUTION_JOB_NAME = args.jobName ?: "${JOB_NAME}"
    def version = inputManifestObj.build.version
    def qualifier = inputManifestObj.build.qualifier ? '-' + inputManifestObj.build.qualifier : ''
    def revision = version + qualifier

    def DISTRIBUTION_PLATFORM = args.platform
    def DISTRIBUTION_ARCHITECTURE = args.architecture
    def distribution = args.distribution
    def previousBuildId = args.previousBuildId ?: "latest"
    def DISTRIBUTION_BUILD_NUMBER

    if (incremental_enabled && previousBuildId.equalsIgnoreCase("latest")) {
        def latestIndexStatus = sh (
                script:  "curl -sL ${PUBLIC_ARTIFACT_URL}/${DISTRIBUTION_JOB_NAME}/${revision}/index/${DISTRIBUTION_PLATFORM}/${DISTRIBUTION_ARCHITECTURE}/${distribution}/index.json | jq -r \".latest\" > /dev/null 2>&1",
                returnStatus: true
        )
        def latestIndexStatusOld = sh (
                script:  "curl -sL ${PUBLIC_ARTIFACT_URL}/${DISTRIBUTION_JOB_NAME}/${revision}/index.json | jq -r \".latest\" > /dev/null 2>&1",
                returnStatus: true
        )
        if (latestIndexStatus == 0) {
            echo("Use new URL path for the latest index.")
            DISTRIBUTION_BUILD_NUMBER = sh(
                    script:  "curl -sL ${PUBLIC_ARTIFACT_URL}/${DISTRIBUTION_JOB_NAME}/${revision}/index/${DISTRIBUTION_PLATFORM}/${DISTRIBUTION_ARCHITECTURE}/${distribution}/index.json | jq -r \".latest\"",
                    returnStdout: true
            ).trim()
        } else if (latestIndexStatusOld == 0) {
            echo("Use old URL path for the latest index.")
            DISTRIBUTION_BUILD_NUMBER = sh(
                    script: "curl -sL ${PUBLIC_ARTIFACT_URL}/${DISTRIBUTION_JOB_NAME}/${revision}/index.json | jq -r \".latest\"",
                    returnStdout: true
            ).trim()
        } else {
            echo("No latest build for ${revision} is available. Building all components from the manifest.")
            incremental_enabled = false
        }
    } else {
        DISTRIBUTION_BUILD_NUMBER = previousBuildId
    }

    if (incremental_enabled) {
        echo("Incremental build enabled! Retrieving previous build library.")
        args.distributionBuildNumber = DISTRIBUTION_BUILD_NUMBER
        retrievePreviousBuild(args)
    }

    sh(([
        './build.sh',
        args.inputManifest ?: "manifests/${INPUT_MANIFEST}",
        args.distribution ? "-d ${args.distribution}" : null,
        args.componentName ? "--component ${args.componentName}" : null,
        args.platform ? "-p ${args.platform}" : null,
        args.architecture ? "-a ${args.architecture}" : null,
        args.snapshot ? '--snapshot' : null,
        args.lock ? '--lock' : null,
        args.continueOnError ? '--continue-on-error' : null,
        incremental_enabled ? '--incremental' : null
    ] - null).join(' '))
}
