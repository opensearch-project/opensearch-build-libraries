/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.BuildManifest

def call(Map args = [:]) {
    sh "curl -sSL ${args.url} --output ${args.path}"
    def buildManifestObj = new BuildManifest(readYaml(file: args.path))
    return buildManifestObj
}
