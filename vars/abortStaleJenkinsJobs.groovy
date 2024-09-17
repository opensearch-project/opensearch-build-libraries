/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to fetch failing tests at the end of gradle-check run and index the results in an OpenSearch cluster.
 *
 * @param Map args = [:] args A map of the following parameters
 * @param args.jobName <required> - The name of the jenkins job.
 * @param args.lookupTime <optional> - Fetch builds from past N hours for the job, defaults to 6 hours.
 */

import java.time.Instant
import java.time.temporal.ChronoUnit
import jenkins.model.Jenkins
import hudson.model.Result

void call(Map args = [:]) {
    String jobName = args.jobName.toString()
    long lookupTime = isNullOrEmpty(args.lookupTime.toString()) ? 6 : Long.parseLong(args.lookupTime.toString())

    if (isNullOrEmpty(jobName)) {
        throw new IllegalArgumentException("Error: jobName is null or empty")
    }

    def currentBuildNumber = currentBuild.number
    def currentBuildDescription = currentBuild.description
    def endTime = Instant.now()
    def startTime = endTime.minus(lookupTime, ChronoUnit.HOURS)
    def startMillis = startTime.toEpochMilli()
    def endMillis = endTime.toEpochMilli()

    // Add sleep to let job-id get assigned to queued jobs when triggered via generic webhook url
    sleep(15)

    def currentJob = Jenkins.instance.getItemByFullName(jobName)

    //Fetch all builds for the job based on look up time provided
    def builds = currentJob.getBuilds().byTimestamp(startMillis,endMillis)
    for (build in builds) {
        if (build.isBuilding() && currentBuildNumber > build.number && currentBuildDescription == build.description) {
            try {
                build.doStop()
                println "Aborted build #${build.number} for ${build.description}"
            }
            catch (Exception e) {
                if (build.result == Result.ABORTED) {
                    println "Build #${build.number} is already aborted!"
                }
                else {
                    println "Failed to abort build #${build.number}: ${e.message}"
                }
            }
        }
    }
}


boolean isNullOrEmpty(String str) { return (str == 'Null' || str == null || str.allWhitespace || str.isEmpty()) }
