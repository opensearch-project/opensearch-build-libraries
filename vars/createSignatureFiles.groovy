/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright OpenSearch Contributors
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
Closure call() {

    lib = library(identifier: 'jenkins@main', retriever: legacySCM(scm))

    return { args -> signArtifacts(args) }

}
