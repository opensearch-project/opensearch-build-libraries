/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
Closure call() {
    allowedFileTypes = [".tar.gz", ".zip", ".rpm", ".deb"]

    return { argsMap -> body: {

        final foundFiles

        if (isUnix()) {
            // For Unix systems, use the find command to locate files
            foundFiles = sh(script: "find ${argsMap.artifactPath} -type f", returnStdout: true).trim().split('\n')
        } else {
            foundFilesRaw = bat(script: "bash -c \"find ${argsMap.artifactPath} -type f\"", returnStdout: true).trim()
            echo("Found files: ${foundFilesRaw}")
            def foundFilesLines = foundFilesRaw.readLines()
            foundFilesLine = foundFilesLines[-1]  // last line
            echo("foundFilesLine: ${foundFilesLine}")
            foundFiles = foundFilesLine.split('\n')
            echo("foundFiles: ${foundFiles}")
        }

        for (file in foundFiles) {
            def acceptTypeFound = false
            for (fileType in allowedFileTypes) {
                if (file.endsWith(fileType)) {
                    final sha512
                    final basename
                    final hash
                    echo("Creating sha for ${file}")
                    if (isUnix()){
                        sha512 = sh(script: "sha512sum ${file}", returnStdout: true).split()
                        echo("sha512: ${sha512}")
                        hash = sha512[0]
                        basename = sh(script: "basename ${sha512[1]}", returnStdout: true)
                        echo("basename: ${basename}")
                    } else {

                        def sha512Raw = bat(script: "bash -c \"sha512sum '${file}'\"", returnStdout: true).trim()
                        def sha512Lines = sha512Raw.readLines()
                        def sha512Line = sha512Lines[-1]  // last line
                        echo("sha512Line: ${sha512Line}")
                        sha512 = sha512Line.trim().split(/\s+/)
                        echo("sha512: ${sha512[0]}")
                        basenameRaw = bat(script: "bash -c \"basename '${sha512[1]}'\"", returnStdout: true, ).trim()
                        def basenameLines = basenameRaw.readLines()
                        basename = basenameLines[-1]  // last line
                        echo("basename: ${basename}")
                    }
                    //sha512 is an array [shasum, filename]
                    // writing to file accroding to opensearch requirement - "512shaHash<space><space>basename"
                    writeFile file: "${file}.sha512", text: "${sha512[0]}  ${basename}"
                    acceptTypeFound = true
                    break
                }
            }
            if (!acceptTypeFound) {
                if(foundFiles.length == 1){
                    echo("Not generating sha for ${file} with artifact Path ${argsMap.artifactPath}, doesn't match allowed types ${allowedFileTypes}")
                } else {
                    echo("Not generating sha for ${file} in ${argsMap.artifactPath}, doesn't match allowed types ${allowedFileTypes}")
                }
            }
        }

    }}
}