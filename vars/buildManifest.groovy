/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /** Library to download previous build artifacts used for incremental build.
 @param Map args = [:] args A map of the following parameters
 @param args.inputManifest <required> - Relative path to input manifest containing all the components to build.
 @param args.distribution <optional> - Type of distribution to build. Defaults to null.
 @param args.componentName <optional> - Name of the single component to build. Defaults to null and builds all the components in the manifest.
 @param args.platform <optional> - Platform to build. Defaults to null.
 @param args.architecture <optional> - Architecture to build. Defaults to null.
 @param args.snapshot <optional> - Boolean value. Defaults to null.
 @param args.lock <optional> - Generate a stable reference manifest. Defaults to null.
 @param args.continueOnError <optional> - Do not fail the distribution build on any plugin component failure. Defaults to null
 */
void call(Map args = [:]) {
    boolean incremental_enabled = args.incremental != null && !args.incremental.isEmpty() && !args.incremental.equalsIgnoreCase("false")

    if (incremental_enabled) {
        echo("Incremental build enabled! Retrieving previous build library")
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
