/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

 /** Library to run Gradle Check Tasks in OpenSearch repo
 *  The library triggers gradle check task from a Pull Request or through Timer (cron based runs)
 *  @param Map args = [:] args A map of the following parameters
 *  @param args.gitRepoUrl <required> - Github repo url generally - https://github.com/opensearch-project/OpenSearch.git  is cloned and checks tasks are executed in it.
 *  @param args.gitReference <optional> - The git commit or branch that needs to be checked in OpenSearch repo to run check tasks defaults to main.
 *  @param args.bwcCheckoutAlign <optional> - Used to set the value of bwc.checkout.align, can be either true or false
 *  @param args.module_name <optional> - Defines module scope which has check tasks running and reported against it.
 **/

void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@11.4.0', retriever: legacySCM(scm))
    def git_repo_url = args.gitRepoUrl ?: 'null'
    def git_reference = args.gitReference ?: 'null'
    def module_name = args.scope ?: 'null'
    def bwc_checkout_align = args.bwcCheckoutAlign ?: 'false'
    def bwc_checkout_align_param = ''
    def command
    println("Git Repo: ${git_repo_url}")
    println("Git Reference: ${git_reference}")
    println("Bwc Checkout Align: ${bwc_checkout_align}")
    println("Module Scope: ${module_name}")

    if (Boolean.parseBoolean(bwc_checkout_align)) {
        bwc_checkout_align_param = '-Dbwc.checkout.align=true'
    }

    if (git_repo_url.equals('null') || git_reference.equals('null') || module_name.equals('null')) {
        println("git repo url or git reference or module_name aren't specified to checkout the commit and run gradle check task, exit 1")
        System.exit(1)
    }
    else {
        switch (args.scope) {
            case 'server':
                command = ':server:check -Dmoduletests.coverage=true'
                break
            case 'non-server':
                command = 'check -x :server:check -Dtests.coverage=true'
                break
            default:
                command = 'check -Dtests.coverage=true'
                break
        }

        def secret_s3 = [
            [envVar: 'amazon_s3_access_key', secretRef: 'op://opensearch-infra-secrets/gradle-check/jenkins-gradle-check-s3-aws-access-key'],
            [envVar: 'amazon_s3_secret_key', secretRef: 'op://opensearch-infra-secrets/gradle-check/jenkins-gradle-check-s3-aws-secret-key'],
            [envVar: 'amazon_s3_base_path', secretRef: 'op://opensearch-infra-secrets/gradle-check/jenkins-gradle-check-s3-aws-base-path'],
            [envVar: 'amazon_s3_bucket', secretRef: 'op://opensearch-infra-secrets/gradle-check/jenkins-gradle-check-s3-aws-bucket-name']
        ]

        withSecrets(secrets: secret_s3){

            sh """
                #!/bin/bash

                set -e
                set +x

                echo "Git clone: ${git_repo_url} with ref: ${git_reference}"
                rm -rf search
                git clone ${git_repo_url} search
                cd search/
                git checkout -f ${git_reference}
                git rev-parse HEAD

                echo "Get Major Version"
                OS_VERSION=`cat gradle/libs.versions.toml | grep opensearch | cut -d= -f2 | grep -oE '[0-9.]+'`
                JDK_MAJOR_VERSION=`cat gradle/libs.versions.toml | grep "bundled_jdk" | cut -d= -f2 | grep -oE '[0-9]+'  | head -n 1`
                OS_MAJOR_VERSION=`echo \$OS_VERSION | grep -oE '[0-9]+' | head -n 1`
                echo "Version: \$OS_VERSION, Major Version: \$OS_MAJOR_VERSION"

                echo "Using JAVA \$JDK_MAJOR_VERSION"
                eval export JAVA_HOME='\$JAVA'\$JDK_MAJOR_VERSION'_HOME'

                env | grep JAVA | grep HOME

                echo "Gradle clean cache and stop existing gradledaemon"
                ./gradlew --stop
                rm -rf ~/.gradle

                if command -v docker > /dev/null; then
                    echo "Check existing dockercontainer"
                    docker ps -a
                    docker stop `docker ps -qa` > /dev/null 2>&1 || echo
                    docker rm --force `docker ps -qa` > /dev/null 2>&1 || echo
                    echo "Stop existing dockercontainer"
                    docker ps -a

                    echo "Check docker-compose version"
                    docker-compose version
                fi

                echo "Check existing processes"
                ps -ef | grep [o]pensearch | wc -l
                echo "Cleanup existing processes"
                kill -9 `ps -ef | grep [o]pensearch | awk '{print \$2}'` > /dev/null 2>&1 || echo
                ps -ef | grep [o]pensearch | wc -l

                echo "Start gradlecheck"
                GRADLE_CHECK_STATUS=0
                ./gradlew clean && ./gradlew ${command} ${bwc_checkout_align_param} --no-daemon --no-scan || GRADLE_CHECK_STATUS=1

                if [ "\$GRADLE_CHECK_STATUS" != 0 ]; then
                    echo Gradle Check Failed!
                    exit 1
                fi

            """
        }

    }


}
