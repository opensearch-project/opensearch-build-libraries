/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /** Library to find a pattern in jenkins build log.
 @param Map args = [:] args A map of the following parameters
 @param args.search <required> - Use 'pass' to get the components passed and 'fail' for components failed.
 */
import com.cloudbees.groovy.cps.NonCPS
import org.apache.commons.io.IOUtils
@NonCPS
def call(Map args = [:]){
    String QUERY_STRING = ''
    if (args.search == 'fail') {
       QUERY_STRING = "Error building"
    } else if (args.search == 'pass') {
       QUERY_STRING = "Build successful"
    }
    List<String> message = []
    Reader performance_log = currentBuild.getRawBuild().getLogReader()
    String logContent = IOUtils.toString(performance_log)
    performance_log.close()
    performance_log = null
    logContent.eachLine() { line ->
        line=line.replace("\"", "")
        //Gets the exact match for Error building
        def java.util.regex.Matcher match = (line =~ /$QUERY_STRING.*/)
        if (match.find()) {
            line=match[0]
            message.add(line)
        }
    }
    //if no match returns as Build failed
    if(message.isEmpty()){
        message=["Build failed"]
    }
    return message
}
