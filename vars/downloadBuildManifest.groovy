/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
def call(Map args = [:]) {
   def lib = library(identifier: "jenkins@10.2.3", retriever: legacySCM(scm))

   if (isUnix()) {
       sh "mkdir -p \$(dirname \"${args.path}\")"
       sh "touch ${args.path}"
       sh "curl -sSL --retry 5 ${args.url} --output ${args.path}"
   } else {
       bat """
           bash -c "mkdir -p \$(dirname \"${args.path}\")"
       """
       bat """
           bash -c "touch ${args.path}"
       """
       bat """
           bash -c "curl -sSL --retry 5 ${args.url} --output ${args.path}"
       """
   }

   def buildManifestObj = lib.jenkins.BuildManifest.new(readYaml(file: args.path))
   return buildManifestObj
}
