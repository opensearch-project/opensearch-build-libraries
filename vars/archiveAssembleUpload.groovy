/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.BuildManifest
import jenkins.InputManifest

def call(Map args = [:]) {
    def inputManifestObj = new InputManifest(readYaml(file: args.inputManifest))

    String stashName = "${args.stashName}"
    echo "Unstashing ${stashName} before starting the assemble process"
    unstash "${stashName}"

    echo "Assembling ${args.inputManifest}"

    String buildManifest = "${args.distribution}/builds/${inputManifestObj.build.getFilename()}/manifest.yml"
    def buildManifestObj = new BuildManifest(readYaml(file: buildManifest))

    assembleUpload(
        args + [
            buildManifest: buildManifest,
        ]
    )

    return buildManifestObj
}
