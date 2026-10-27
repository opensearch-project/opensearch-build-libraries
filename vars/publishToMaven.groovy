/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to sign and deploy opensearch artifacts to the Sonatype Central Publisher Portal.
 @param Map args = [:] args A map of the following parameters
 @param args.signingArtifactsPath <required> - Local Path to yml or artifact file. This could be path to Dir that contains artifacts or full path to yml file.
 @param args.mavenArtifactsPath <required> - The local directory containing distribution files to upload to the repository. example: ~/.m2/repository where repository contains /org/opensearch
 @param args.autoPublish <optional> - Set it to true to auto-publish maven artifacts to Maven Central once validation passes. Default is false.
 @param args.multiPartsUpload <optional> - Set it to true to upload folders under org/opensearch in parts of up to 500MB, skipping the ones already on Maven Central. Default is false.
 @param args.skipPublishedCheck <optional> - Set it to true to skip the Maven Central published check in multiPartsUpload. Default is false.
 */

void call(Map args = [:]) {
    lib = library(identifier: 'jenkins@13.11.0', retriever: legacySCM(scm))
    loadCustomScript(scriptPath: 'publish/stage-maven-release.sh',
                     scriptName: 'stage-maven-release.sh')
    def signingArtifactsPath = args.signingArtifactsPath
    def mavenArtifactsPath = args.mavenArtifactsPath
    // No toBoolean() here so an invalid value is rejected by stage-maven-release.sh instead of silently becoming false
    def autoPublish = args.autoPublish ?: false
    def multiPartsUpload = args.multiPartsUpload?.toBoolean() ?: false
    def skipPublishedCheck = args.skipPublishedCheck?.toBoolean() ?: false
    def email = args.email ?: "release@opensearch.org"
    if (!signingArtifactsPath || !mavenArtifactsPath) {
        error('signingArtifactsPath and mavenArtifactsPath are required.')
    }
    println("Signing Maven artifacts.")
    signArtifacts(
            artifactPath: signingArtifactsPath,
            type: 'maven',
            platform: 'linux',
            sigtype: '.asc',
            email: "${email}",
    )

    println("Stage and Release Maven artifacts.")

    def secret_maven_central = [
        [envVar: 'SONATYPE_USERNAME', secretRef: 'op://opensearch-release-secrets/maven-central-portal-credentials/username'],
        [envVar: 'SONATYPE_PASSWORD', secretRef: 'op://opensearch-release-secrets/maven-central-portal-credentials/password']
    ]

    withSecrets(secrets: secret_maven_central){
            if (multiPartsUpload) {
                println("Uploading Maven artifacts in multiple parts.")
                sh """#!/bin/bash
                    set -e
                    sonatype_auth="Authorization: Bearer \$(printf '%s:%s' "\${SONATYPE_USERNAME}" "\${SONATYPE_PASSWORD}" | base64 | tr -d '\\n')"
                    parts_dir="${mavenArtifactsPath}/multiparts"
                    part_folders=""
                    part_size=0
                    failed_folders=""
                    part_count=0
                    parts_summary=()
                    upload_prefix="\$(date -u +%Y-%m-%d)-${BUILD_NUMBER}"

                    upload_part() {
                        part_count=\$((part_count + 1))
                        part_size_mb=\$((part_size / 1024))
                        part_files=\$(find "\${parts_dir}" -type f | wc -l | tr -d ' ')
                        part_folder_count=\$(echo \${part_folders} | wc -w | tr -d ' ')
                        echo "Uploading Part \${part_count} (\${part_size_mb}MB, \${part_folder_count} folders, \${part_files} files):\${part_folders}"
                        if ./stage-maven-release.sh -d "\${parts_dir}" -a ${autoPublish} -n "\${upload_prefix}-part\${part_count}"; then
                            part_status="uploaded"
                        else
                            part_status="failed"
                            failed_folders="\${failed_folders}\${part_folders}"
                        fi
                        echo "Part \${part_count} \${part_status}."
                        parts_summary+=("Part \${part_count} (\${part_size_mb}MB, \${part_folder_count} folders, \${part_files} files): \${part_status}")
                        rm -rf "\${parts_dir}"
                        part_folders=""
                        part_size=0
                    }

                    # Pack folders into parts of up to 500MB to stay well under the Portal 1GB bundle limit
                    for folder in \$(cd "${mavenArtifactsPath}" && ls -d org/opensearch/* org/opensearch/plugin/* | grep -vx 'org/opensearch/plugin'); do

                        # Verify pom status
                        pom=\$(cd "${mavenArtifactsPath}" && find "\${folder}" -name '*.pom' | head -1)
                        if [ -z "\${pom}" ]; then
                            echo "Skipping \${folder}, no pom found."
                            failed_folders="\${failed_folders} \${folder}"
                            continue
                        fi

                        # Verify Maven Central publish, first pom only as all poms in a folder are published together
                        if [ "${skipPublishedCheck}" != "true" ]; then
                            version_dir=\${pom%/*}
                            artifact_dir=\${version_dir%/*}
                            namespace=\$(echo "\${artifact_dir%/*}" | tr / .)
                            name=\${artifact_dir##*/}
                            version=\${version_dir##*/}
                            published=\$(curl -s --max-time 30 -H "\${sonatype_auth}" "https://central.sonatype.com/api/v1/publisher/published?namespace=\${namespace}&name=\${name}&version=\${version}" | jq -R -r 'fromjson? | .published // empty')
                            if [ "\${published}" = "true" ]; then
                                echo "Skipping \${folder}, already on Maven Central."
                                continue
                            fi
                        fi

                        # Verify folder size
                        size=\$(du -sk "${mavenArtifactsPath}/\${folder}" | cut -f1)
                        if [ "\${part_size}" -gt 0 ] && [ \$((part_size + size)) -gt 512000 ]; then
                            upload_part
                        fi
                        echo "Adding \${folder}."
                        mkdir -p "\${parts_dir}/\$(dirname "\${folder}")"
                        mv "${mavenArtifactsPath}/\${folder}" "\${parts_dir}/\${folder}"
                        part_folders="\${part_folders} \${folder}"
                        part_size=\$((part_size + size))
                    done

                    # One last push
                    if [ "\${part_size}" -gt 0 ]; then
                        upload_part
                    fi

                    # Output parts summary
                    echo "==========================================="
                    echo "Multi-parts upload summary:"
                    printf '%s\\n' "\${parts_summary[@]}"
                    echo "==========================================="

                    # Output failure folders
                    if [ -n "\${failed_folders}" ]; then
                        echo "Failed to upload:\${failed_folders}"
                        exit 1
                    fi
                """
            } else {
                println("Uploading Maven artifacts in a single part.")
                sh("./stage-maven-release.sh -d ${mavenArtifactsPath} -a ${autoPublish}")
            }
        }
}
