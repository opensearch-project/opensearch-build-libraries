/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to load custom scripts from resources dir and make them available in the runtime environment to be executed.
 * See https://www.jenkins.io/doc/book/pipeline/shared-libraries/#loading-resources for more details
 @param Map args = [:] args A map of the following parameters
 @param args.scriptPath <required> - Relative Path to the existing script present in resources dir. The path is relative to resources dir. e.g., 'publish/stage-maven-release.sh'
 @param args.scriptName <required> - The name of the file that will hold the actual script code, can be same as the existing script name.
 */
void call(Map args = [:]) {
    def scriptContent = libraryResource "${args.scriptPath}"
    writeFile file: "${args.scriptName}", text: scriptContent
    sh "chmod a+x ./${args.scriptName}"
}
