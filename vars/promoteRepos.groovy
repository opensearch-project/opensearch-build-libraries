/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */


 /**
 * Library to sign and promote repositories to artifacts.opensearch.org
@param Map[jobName] <required> - Name of the distribution build workflow on Jenkins that build the artifacts
@param Map[buildNumber] <required> - The build number of the artifacts in above build workflow
@param Map[distributionRepoType] <required> - distribution repository type, e.g. yum / apt
@param Map[manifest] <optional> - input manifest of the corresponding OpenSearch/OpenSearch-Dashboards product version (e.g. 2.0.0)
 */

void call(Map args = [:]) {
    def lib = library(identifier: 'jenkins@2.1.0', retriever: legacySCM(scm))

    String manifest = args.manifest ?: "manifests/${INPUT_MANIFEST}"
    def inputManifest = lib.jenkins.InputManifest.new(readYaml(file: manifest))

    String filename = inputManifest.build.getFilename()
    String jobname = args.jobName

    String buildnumber = args.buildNumber ?: 'none'
    if (buildnumber == 'none') {
        println('User did not enter build number in jenkins parameter, exit 1')
        System.exit(1)
    }

    def distTypeMap = [
        "yum": "rpm",
        "apt": "deb",
    ]
    String repoType = args.distributionRepoType
    String distType = distTypeMap["${repoType}"]
    String version = inputManifest.build.version
    String majorVersion = version.tokenize('.')[0]
    String repoVersion = majorVersion + '.x'
    String qualifier = inputManifest.build.qualifier ? '-' + inputManifest.build.qualifier : ''
    String revision = version + qualifier
    println("Product: ${filename}")
    println("Build Number: ${buildnumber}")
    println("Input Manifest: ${manifest}")
    println("Revision: ${revision}")
    println("Major Version: ${majorVersion}")
    println("Repo Type: ${repoType}")
    println("Dist Type: ${distType}")
    println("Repo Version: ${repoVersion}")

    String stagingPkgPathX64 = "${PUBLIC_ARTIFACT_URL}/${jobname}/${revision}/${buildnumber}/linux/x64/${distType}/dist/${filename}/${filename}-${revision}-linux-x64.${distType}"
    String stagingPkgPathARM64 = "${PUBLIC_ARTIFACT_URL}/${jobname}/${revision}/${buildnumber}/linux/arm64/${distType}/dist/${filename}/${filename}-${revision}-linux-arm64.${distType}"

    String localPath = "${WORKSPACE}/artifacts"
    String RepoProdPath = "releases/bundle/${filename}/${repoVersion}/${repoType}"
    String artifactPath = "${localPath}/${RepoProdPath}"

    withCredentials([string(credentialsId: 'jenkins-artifact-promotion-role', variable: 'ARTIFACT_PROMOTION_ROLE_NAME'),
        string(credentialsId: 'jenkins-aws-production-account', variable: 'AWS_ACCOUNT_ARTIFACT'),
        string(credentialsId: 'jenkins-artifact-production-bucket-name', variable: 'ARTIFACT_PRODUCTION_BUCKET_NAME')]) {
            withAWS(role: "${ARTIFACT_PROMOTION_ROLE_NAME}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
                println("Pulling Prod ${repoType}")
                sh("aws s3 sync s3://${ARTIFACT_PRODUCTION_BUCKET_NAME}/${RepoProdPath}/ ${artifactPath}/ --no-progress")
            }

        sh """
            set -e
            set +x

            echo "Pulling ${revision} ${distType}"
            cd ${artifactPath}
            curl -SLO ${stagingPkgPathX64}
            curl -SLO ${stagingPkgPathARM64}

            ls -l
        """

        if (repoType.equals("yum")) {

            println("Yum Repo Starts")

            sh """
                set -e
                set +x

                cd ${artifactPath}
                rm -vf repodata/repomd.xml.asc
    
                echo "Update repo metadata"
                createrepo --update .
    
                # Rename .xml to .pom for signing
                # Please do not add .xml to signer filter
                # As maven have many .xml and we do not want to sign them
                # This is an outlier case for yum repo only
                mv -v repodata/repomd.xml repodata/repomd.pom
    
                echo "Complete metadata update, awaiting signing repomd.xml"
                cd -
            """

            signArtifacts(
                artifactPath: "${artifactPath}/repodata/repomd.pom",
                sigtype: '.asc',
                platform: 'linux'
            )

            sh """
                set -e
                set +x
    
                cd ${artifactPath}/repodata/
    
                ls -l
    
                mv -v repomd.pom repomd.xml
                mv -v repomd.pom.asc repomd.xml.asc
    
                ls -l
    
                cd -
            """
        }

        if (repoType.equals("apt")) {

            println("Apt Repo Starts")

            sh """#!/bin/bash
                set -e
                set +x

                ARTIFACT_PATH="${artifactPath}"

                echo "------------------------------------------------------------------------"
                echo "Check Utility Versions"
                gpg_version_requirement="2.2.0"
                aptly_version_requirement="1.5.0"

                gpg_version_check=`gpg --version | head -n 1 | grep -oE '[0-9.]+'`
                gpg_version_check_final=`echo \$gpg_version_check \$gpg_version_requirement | tr ' ' '\n' | sort -V | head -n 1`
                aptly_version_check=`aptly version | head -n 1 | grep -oE '[0-9.]+'`
                aptly_version_check_final=`echo \$aptly_version_check \$aptly_version_requirement | tr ' ' '\n' | sort -V | head -n 1`
               
                echo -e "gpg_version_requirement gpg_version_check"
                echo -e "\$gpg_version_requirement \$gpg_version_check"
                echo -e "aptly_version_requirement aptly_version_check"
                echo -e "\$aptly_version_requirement \$aptly_version_check"

                if [[ \$gpg_version_requirement = \$gpg_version_check_final ]] && [[ \$aptly_version_requirement = \$aptly_version_check_final ]]; then
                    echo "Utility version is equal or greater than set limit, continue."
                else
                    echo "Utility version is lower than set limit, exit 1"
                    exit 1
                fi

            """

            withCredentials([
            string(credentialsId: 'jenkins-rpm-signing-account-number', variable: 'RPM_SIGNING_ACCOUNT_NUMBER'),
            string(credentialsId: 'jenkins-rpm-signing-passphrase-secrets-arn', variable: 'RPM_SIGNING_PASSPHRASE_SECRETS_ARN'),
            string(credentialsId: 'jenkins-rpm-signing-secret-key-secrets-arn', variable: 'RPM_SIGNING_SECRET_KEY_ID_SECRETS_ARN'),
            string(credentialsId: 'jenkins-rpm-signing-key-id', variable: 'RPM_SIGNING_KEY_ID')]) {
                withAWS(role: 'jenkins-prod-rpm-signing-assume-role', roleAccount: "${RPM_SIGNING_ACCOUNT_NUMBER}", duration: 900, roleSessionName: 'jenkins-signing-session') {
                    sh """#!/bin/bash

                        export GPG_TTY=`tty`

                        echo "------------------------------------------------------------------------"
                        echo "Import OpenSearch keys"
                        aws secretsmanager get-secret-value --region us-west-2 --secret-id "${RPM_SIGNING_PASSPHRASE_SECRETS_ARN}" | jq -r .SecretBinary | base64 --decode > passphrase
                        aws secretsmanager get-secret-value --region us-west-2 --secret-id "${RPM_SIGNING_SECRET_KEY_ID_SECRETS_ARN}" | jq -r .SecretBinary | base64 --decode | gpg --quiet --import --pinentry-mode loopback --passphrase-file passphrase -

                        echo "------------------------------------------------------------------------"
                    """
                }

                sh """#!/bin/bash

                     set -e
                     set +x

                     ARTIFACT_PATH="${artifactPath}"

                     echo "Start Signing Apt"
                     rm -rf ~/.aptly
                     mkdir \$ARTIFACT_PATH/base
                     find \$ARTIFACT_PATH -type f -name "*.deb" | xargs -I {} mv -v {} \$ARTIFACT_PATH/base
                     aptly repo create -distribution=stable -component=main ${jobname}
                     aptly repo add ${jobname} \$ARTIFACT_PATH/base
                     aptly repo show -with-packages ${jobname}
                     aptly snapshot create ${jobname}-${repoVersion} from repo ${jobname}
                     aptly publish snapshot -batch=true -passphrase-file=passphrase ${jobname}-${repoVersion}
                     echo "------------------------------------------------------------------------"
                     echo "Clean up gpg"
                     gpg --batch --yes --delete-secret-keys ${RPM_SIGNING_KEY_ID}
                     gpg --batch --yes --delete-keys ${RPM_SIGNING_KEY_ID}
                     rm -v passphrase
                     echo "------------------------------------------------------------------------"
                     rm -rf \$ARTIFACT_PATH/*
                     cp -rvp ~/.aptly/public/* \$ARTIFACT_PATH/
                     ls \$ARTIFACT_PATH

                """
            }
        }

        withAWS(role: "${ARTIFACT_PROMOTION_ROLE_NAME}", roleAccount: "${AWS_ACCOUNT_ARTIFACT}", duration: 900, roleSessionName: 'jenkins-session') {
            println("Pushing Prod ${repoType}")
            sh("aws s3 sync ${artifactPath}/ s3://${ARTIFACT_PRODUCTION_BUCKET_NAME}/${RepoProdPath}/ --no-progress")
        }
    }
}
