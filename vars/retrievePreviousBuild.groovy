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
 */
void call(Map args = [:]) {
    if (args.incremental == true) {
        retrievePreviousBuild()
    }

}
