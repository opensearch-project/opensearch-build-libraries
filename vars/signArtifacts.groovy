/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/**
SignArtifacts signs the given artifacts and saves the signature in the same directory
@param Map[artifactPath] <Required> - Path to yml or artifact file.
@param Map[platform] <Required> - The distribution platform for signing.
@param Map[component] <Optional> - Path to yml or artifact file.
@param Map[type] <Optional> - Artifact type in the manifest, [type] is required for signing yml.
@param Map[sigtype] <Optional> - The signature type of signing artifacts. e.g. '.sig'. Required for non-yml artifacts signing.
@param Map[overwrite]<Optional> - Allow output artifacts to overwrite the existing artifacts. Defaults to False
@param Map[email]<Optional> - Email used for signing. Default will be handled in build repo sign workflow [https://github.com/opensearch-project/opensearch-build/tree/main/src/sign_workflow].
*/
void call(Map args = [:]) {
    String email = args.email ?: "release@opensearch.org"
    if (args.sigtype.equals('.rpm')) {
        withCredentials([
        string(credentialsId: 'jenkins-rpm-signing-account-number', variable: 'RPM_SIGNING_ACCOUNT_NUMBER'),
        string(credentialsId: 'jenkins-rpm-release-signing-passphrase-secrets-arn', variable: 'RPM_RELEASE_SIGNING_PASSPHRASE_SECRETS_ARN'),
        string(credentialsId: 'jenkins-rpm-release-signing-secret-key-secrets-arn', variable: 'RPM_RELEASE_SIGNING_SECRET_KEY_ID_SECRETS_ARN'),
        string(credentialsId: 'jenkins-rpm-release-signing-key-id', variable: 'RPM_RELEASE_SIGNING_KEY_ID'),
        string(credentialsId: 'jenkins-rpm-signing-passphrase-secrets-arn', variable: 'RPM_SIGNING_PASSPHRASE_SECRETS_ARN'),
        string(credentialsId: 'jenkins-rpm-signing-secret-key-secrets-arn', variable: 'RPM_SIGNING_SECRET_KEY_ID_SECRETS_ARN'),
        string(credentialsId: 'jenkins-rpm-signing-key-id', variable: 'RPM_SIGNING_KEY_ID')]) {
            echo "RPM Add Sign for email ${email}"

            withAWS(role: 'jenkins-prod-rpm-signing-assume-role', roleAccount: "${RPM_SIGNING_ACCOUNT_NUMBER}", duration: 900, roleSessionName: 'jenkins-signing-session') {
                    sh """#!/bin/bash
                        set -e
                        set +x

                        ARTIFACT_PATH="${args.artifactPath}"

                        echo "------------------------------------------------------------------------"
                        echo "Check Utility Versions"
                        gpg_version_requirement="2.2.0"
                        rpm_version_requirement="4.13.0" # https://bugzilla.redhat.com/show_bug.cgi?id=227632

                        gpg_version_check=`gpg --version | head -n 1 | grep -oE '[0-9.]+'`
                        gpg_version_check_final=`echo \$gpg_version_check \$gpg_version_requirement | tr ' ' '\n' | sort -V | head -n 1`
                        rpm_version_check=`rpm --version | head -n 1 | grep -oE '[0-9.]+'`
                        rpm_version_check_final=`echo \$rpm_version_check \$rpm_version_requirement | tr ' ' '\n' | sort -V | head -n 1`

                        echo -e "gpg_version_requirement gpg_version_check"
                        echo -e "\$gpg_version_requirement \$gpg_version_check"
                        echo -e "rpm_version_requirement rpm_version_check"
                        echo -e "\$rpm_version_requirement \$rpm_version_check"

                        if [[ \$gpg_version_requirement = \$gpg_version_check_final ]] && [[ \$rpm_version_requirement = \$rpm_version_check_final ]]; then
                            echo "Utility version is equal or greater than set limit, continue."
                        else
                            echo "Utility version is lower than set limit, exit 1"
                            exit 1
                        fi

                        export GPG_TTY=`tty`

                        PASSPHRASE_SECRETS_ARN="${RPM_RELEASE_SIGNING_PASSPHRASE_SECRETS_ARN}"
                        SECRET_KEY_ID_SECRETS_ARN="${RPM_RELEASE_SIGNING_SECRET_KEY_ID_SECRETS_ARN}"
                        KEY_ID="${RPM_RELEASE_SIGNING_KEY_ID}"
                        KEY_NAME="OpenSearch Project"

                        if [ "${email}" = "opensearch@amazon.com" ]; then
                            PASSPHRASE_SECRETS_ARN="${RPM_SIGNING_PASSPHRASE_SECRETS_ARN}"
                            SECRET_KEY_ID_SECRETS_ARN="${RPM_SIGNING_SECRET_KEY_ID_SECRETS_ARN}"
                            KEY_ID="${RPM_SIGNING_KEY_ID}"
                            KEY_NAME="OpenSearch project"
                        fi

                        echo "------------------------------------------------------------------------"
                        echo "Setup RPM Macros"
                        cp -v scripts/pkg/sign_templates/rpmmacros ~/.rpmmacros
                        sed -i "s/##key_name##/\${KEY_NAME}/g;s/##passphrase_name##/passphrase/g" ~/.rpmmacros
                        cat ~/.rpmmacros

                        echo "------------------------------------------------------------------------"
                        echo "Import OpenSearch keys"
                        aws secretsmanager get-secret-value --region us-west-2 --secret-id "\$PASSPHRASE_SECRETS_ARN" | jq -r .SecretBinary | base64 --decode > passphrase
                        aws secretsmanager get-secret-value --region us-west-2 --secret-id "\$SECRET_KEY_ID_SECRETS_ARN" | jq -r .SecretBinary | base64 --decode | gpg --quiet --import --pinentry-mode loopback --passphrase-file passphrase -

                        echo "------------------------------------------------------------------------"
                        echo "Start Signing Rpm"

                        if file \$ARTIFACT_PATH | grep -q directory; then

                            echo "Sign directory"
                            for rpm_file in `ls \$ARTIFACT_PATH`; do
                                if file \$ARTIFACT_PATH/\$rpm_file | grep -q RPM; then
                                    rpm --addsign \$ARTIFACT_PATH/\$rpm_file
                                    rpm -qip \$ARTIFACT_PATH/\$rpm_file | grep Signature
                                fi
                            done

                        elif file \$ARTIFACT_PATH | grep -q RPM; then
                            echo "Sign single rpm"
                            rpm --addsign \$ARTIFACT_PATH
                            rpm -qip \$ARTIFACT_PATH | grep Signature

                        else
                            echo "This is neither a directory nor a RPM pkg, exit 1"
                            exit 1
                        fi

                        echo "------------------------------------------------------------------------"
                        echo "Clean up gpg"
                        gpg --batch --yes --delete-secret-keys \$KEY_ID
                        gpg --batch --yes --delete-keys \$KEY_ID
                        rm -v passphrase

                    """
            }
        }
    }
    else {
        String workdir = "${WORKSPACE}"

        if (!fileExists("$WORKSPACE/sign.sh")) {
            dir('opensearch-build') {
                git url: 'https://github.com/opensearch-project/opensearch-build.git', branch: 'main'
                workdir = "${WORKSPACE}/opensearch-build"
            }
        }

        String arguments = generateArguments(args)

        // Sign artifacts

        if (args.platform == 'windows') {
            println('Using Windows signing')
            withCredentials([usernamePassword(credentialsId: "${GITHUB_BOT_TOKEN_NAME}", usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN'),
                string(credentialsId: 'jenkins-signer-windows-role', variable: 'SIGNER_WINDOWS_ROLE'),
                string(credentialsId: 'jenkins-signer-windows-external-id', variable: 'SIGNER_WINDOWS_EXTERNAL_ID'),
                string(credentialsId: 'jenkins-signer-windows-unsigned-bucket', variable: 'SIGNER_WINDOWS_UNSIGNED_BUCKET'),
                string(credentialsId: 'jenkins-signer-windows-signed-bucket', variable: 'SIGNER_WINDOWS_SIGNED_BUCKET'),
                string(credentialsId: 'jenkins-signer-windows-profile-identifier', variable: 'SIGNER_WINDOWS_PROFILE_IDENTIFIER'),
                string(credentialsId: 'jenkins-signer-windows-platform-identifier', variable: 'SIGNER_WINDOWS_PLATFORM_IDENTIFIER')]) {
                sh """#!/bin/bash
                   set +x
                   export ROLE=$SIGNER_WINDOWS_ROLE
                   export EXTERNAL_ID=$SIGNER_WINDOWS_EXTERNAL_ID
                   export UNSIGNED_BUCKET=$SIGNER_WINDOWS_UNSIGNED_BUCKET
                   export SIGNED_BUCKET=$SIGNER_WINDOWS_SIGNED_BUCKET
                   export PROFILE_IDENTIFIER=$SIGNER_WINDOWS_PROFILE_IDENTIFIER
                   export PLATFORM_IDENTIFIER=$SIGNER_WINDOWS_PLATFORM_IDENTIFIER

                   ${workdir}/sign.sh ${arguments}
               """
                }
        }
        else if (args.platform == 'mac') {
            println('Using MAC signing')
            withCredentials([usernamePassword(credentialsId: "${GITHUB_BOT_TOKEN_NAME}", usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN'),
                string(credentialsId: 'jenkins-signer-mac-role', variable: 'SIGNER_MAC_ROLE'),
                string(credentialsId: 'jenkins-signer-mac-external-id', variable: 'SIGNER_MAC_EXTERNAL_ID'),
                string(credentialsId: 'jenkins-signer-mac-unsigned-bucket', variable: 'SIGNER_MAC_UNSIGNED_BUCKET'),
                string(credentialsId: 'jenkins-signer-mac-signed-bucket', variable: 'SIGNER_MAC_SIGNED_BUCKET')]) {
                sh """#!/bin/bash
                   set +x
                   export ROLE=$SIGNER_MAC_ROLE
                   export EXTERNAL_ID=$SIGNER_MAC_EXTERNAL_ID
                   export UNSIGNED_BUCKET=$SIGNER_MAC_UNSIGNED_BUCKET
                   export SIGNED_BUCKET=$SIGNER_MAC_SIGNED_BUCKET
                   ${workdir}/sign.sh ${arguments}
               """
                }
        }
        else if (args.platform == 'jar_signer') {
            println('Using jar signing')
            withCredentials([usernamePassword(credentialsId: "${GITHUB_BOT_TOKEN_NAME}", usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN'),
                string(credentialsId: 'jenkins-jar-signer-role', variable: 'JAR_SIGNER_ROLE'),
                string(credentialsId: 'jenkins-jar-signer-external-id', variable: 'JAR_SIGNER_EXTERNAL_ID'),
                string(credentialsId: 'jenkins-jar-signer-unsigned-bucket', variable: 'JAR_SIGNER_UNSIGNED_BUCKET'),
                string(credentialsId: 'jenkins-jar-signer-signed-bucket', variable: 'JAR_SIGNER_SIGNED_BUCKET')]) {
                sh """#!/bin/bash
                   set +x
                   export ROLE=$JAR_SIGNER_ROLE
                   export EXTERNAL_ID=$JAR_SIGNER_EXTERNAL_ID
                   export UNSIGNED_BUCKET=$JAR_SIGNER_UNSIGNED_BUCKET
                   export SIGNED_BUCKET=$JAR_SIGNER_SIGNED_BUCKET
                   ${workdir}/sign.sh ${arguments}
               """
                }
        }
        else {
            println('Using PGP signing')
            importPGPKey()
            withCredentials([usernamePassword(credentialsId: "${GITHUB_BOT_TOKEN_NAME}", usernameVariable: 'GITHUB_USER', passwordVariable: 'GITHUB_TOKEN'),
                string(credentialsId: 'jenkins-signer-client-role', variable: 'SIGNER_CLIENT_ROLE'),
                string(credentialsId: 'jenkins-signer-client-external-id', variable: 'SIGNER_CLIENT_EXTERNAL_ID'),
                string(credentialsId: 'jenkins-signer-client-unsigned-bucket', variable: 'SIGNER_CLIENT_UNSIGNED_BUCKET'),
                string(credentialsId: 'jenkins-signer-client-signed-bucket', variable: 'SIGNER_CLIENT_SIGNED_BUCKET')]) {
                sh """#!/bin/bash
                   set +x
                   export ROLE=$SIGNER_CLIENT_ROLE
                   export EXTERNAL_ID=$SIGNER_CLIENT_EXTERNAL_ID
                   export UNSIGNED_BUCKET=$SIGNER_CLIENT_UNSIGNED_BUCKET
                   export SIGNED_BUCKET=$SIGNER_CLIENT_SIGNED_BUCKET

                   ${workdir}/sign.sh ${arguments}
               """
                }
        }
    }
}

String generateArguments(args) {
    String artifactPath = args.remove('artifactPath')
    // artifactPath is mandatory and the first argument
    String arguments = artifactPath

    // overwrite does not take an argument so removing it
    if (args['overwrite'] == true) {
        args['overwrite'] = ''
    } else {
        args.remove('overwrite')
    }
    // generation command line arguments
    args.each { key, value -> arguments += " --${key } ${value }" }
    return arguments
}

void importPGPKey() {
    sh 'curl -sSL https://artifacts.opensearch.org/publickeys/opensearch.pgp | gpg --import - ' +
            '&& curl -sSL https://artifacts.opensearch.org/publickeys/opensearch-release.pgp | gpg --import -'
}
