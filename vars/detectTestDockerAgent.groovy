/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import jenkins.TestManifest

Map call(Map args = [:]) {
    String manifest = args.testManifest ?: "manifests/${TEST_MANIFEST}"
    def testManifest = new TestManifest(readYaml(file: manifest))
    dockerImage = testManifest.ci?.image?.name ?: 'opensearchstaging/ci-runner:ci-runner-centos7-v1'
    dockerArgs = testManifest.ci?.image?.args
    echo "Using Docker image ${dockerImage} (${dockerArgs})"
    return [
        image: dockerImage,
        args: dockerArgs
    ]
}
