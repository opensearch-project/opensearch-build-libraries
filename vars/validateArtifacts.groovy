/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/**
Wrapper that runs validation.sh script with provided args.
@param Map[<any>] <Required> - Any arguments that you want to be passed to validation.sh script. eg: version: 1.0.0 will be passed as --version 1.0.0
*/
void call(Map args = [:]) {
    if (!fileExists("$WORKSPACE/validation.sh")) {
        println("Validation.sh script not found, exit 1")
        System.exit(1)
    }
    String arguments = generateArguments(args)
    sh "${WORKSPACE}/validation.sh ${arguments}"
}

String generateArguments(args) {
    String arguments = ''
    // generate of command line arguments
    args.each { key, value -> arguments += " --${key } ${value }" }
    return arguments
}
