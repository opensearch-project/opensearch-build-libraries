#!/bin/bash

###### Information ############################################################################
# Copyright OpenSearch Contributors
# SPDX-License-Identifier: Apache-2.0
#
# The OpenSearch Contributors require contributions made to
# this file be licensed under the Apache-2.0 license or a
# compatible open source license.
#
# Name:          stage-maven-release.sh
# Language:      Shell
#
# About:         Deploy opensearch artifacts to a maven central.
#                This script will create a new staging repository in Sonatype and stage
#                all artifacts in the passed in directory. If AUTO_PUBLISH is enabled, 
#                it will publish to maven central. The folder passed as input should contain 
#                subfolders org/opensearch to ensure artifacts are deployed under the correct groupId.
#                Example: ./stage-maven-release.sh /maven
#                - where maven contains /maven/org/opensearch
#
# Usage:         ./stage-maven-release.sh -d <directory> -a <true|false>
#
###############################################################################################
set -e

usage() {
  echo "usage: $0 [-h] -d <path_to_artifacts_dir> -a <true|false>"
  echo "  -h      display help"
  echo "  -d      parent directory containing artifacts to org/opensearch namespace."
  echo "          example: dir = ~/.m2/repository where repository contains /org/opensearch"
  echo "  -a      auto-publish to maven central after staging repository is created. Defaults to false."
  echo "Required environment variables:"
  echo "SONATYPE_USERNAME - username with publish rights to a sonatype repository"
  echo "SONATYPE_PASSWORD - publishing token for sonatype"
  echo "STAGING_PROFILE_ID - Sonatype Staging profile ID"
  exit 1
}
AUTO_PUBLISH=false

while getopts "ha:d:" option; do
  case $option in
  h)
    usage
    ;;
  a)
    AUTO_PUBLISH="${OPTARG}"
    ;;
  d)
    ARTIFACT_DIRECTORY="${OPTARG}"
    ;;
  \?)
    echo "Invalid option -$OPTARG" >&2
    usage
    exit 1
    ;;
  esac
done

if [ "$AUTO_PUBLISH" != "true" ] && [ "$AUTO_PUBLISH" != "false" ]; then
  echo "Error: Invalid value for -a: '$AUTO_PUBLISH'. Must be 'true' or 'false'"
  usage
  exit 1
fi

required_env_vars=(ARTIFACT_DIRECTORY SONATYPE_USERNAME SONATYPE_PASSWORD STAGING_PROFILE_ID)
for var in "${required_env_vars[@]}"; do
  if [ -z "${!var}" ]; then
    echo "Error: $var is required"
    usage
    exit 1
  fi
done

if [ ! -d "$ARTIFACT_DIRECTORY" ]; then
  echo "Invalid directory $ARTIFACT_DIRECTORY does not exist"
  usage
fi

[ ! -d "$ARTIFACT_DIRECTORY"/org/opensearch ] && {
  echo "Given directory does not contain opensearch artifacts"
  usage
}

workdir=$(mktemp -d)

function cleanup() {
  rm -rf "${workdir}"
}
trap cleanup TERM INT EXIT

function create_maven_settings() {
  # Create a settings.xml file with the user+password for maven
  mvn_settings="${workdir}/mvn-settings.xml"
  cat >"${mvn_settings}" <<-EOF
<?xml version="1.0" encoding="UTF-8" ?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                            http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>${SONATYPE_USERNAME}</username>
      <password>${SONATYPE_PASSWORD}</password>
    </server>
  </servers>
</settings>
EOF
}

create_maven_settings

echo "AUTO_PUBLISH variable is set to: '$AUTO_PUBLISH'"
echo "==========================================="
echo "Deploying artifacts under ${ARTIFACT_DIRECTORY} to Staging Repository."
echo "==========================================="

deployment=$(mvn --settings="${mvn_settings}" \
  org.sonatype.plugins:nexus-staging-maven-plugin:1.6.13:deploy-staged-repository \
  -DrepositoryDirectory="${ARTIFACT_DIRECTORY}" \
  -DnexusUrl="https://ossrh-staging-api.central.sonatype.com" \
  -DserverId=central \
  -DautoReleaseAfterClose=false \
  -DstagingProgressTimeoutMinutes=30 \
  -DstagingProfileId="${STAGING_PROFILE_ID}" | tee /dev/stderr)

if echo "$deployment" | grep "BUILD SUCCESS"; then
  deployed_staging_repo_id=$(echo $deployment | grep "Closing staging repository with ID" | grep -o "\"[^\"]*\"" | tr -d '"')
  echo "Successfully staged and validated artifacts. Staging repository ID: ${deployed_staging_repo_id}"
else
  echo "Deployment failed!! Please check the logs above for details or check the Sonatype portal https://central.sonatype.com/publishing ."
  exit 1
fi

echo "==========================================="
echo "Done."
echo "==========================================="

# When using `org.sonatype.plugins:nexus-staging-maven-plugin` rc-close or rc-release we get below error:
# `Failed to process request: Got unexpected XML element when reading stagedRepositoryIds: Got unexpected element StartElement(a, {"": "", "xml": "http://www.w3.org/XML/1998/namespace", "xmlns": "http://www.w3.org/2000/xmlns/"}, [class -> string-array]), expected one of: string`
# Sending raw POST request to release the staging repository instead.
# Ref: https://github.com/cdklabs/publib/pull/1667/files#diff-36ff5f7d55e47535ad5f6a8236eaecc92dba5cc2223d39b09f870d090c47327eR396

if [ "$AUTO_PUBLISH" = true ] ; then
    echo "==========================================="
    echo "Releasing Staging Repository ${deployed_staging_repo_id}."
    echo "==========================================="

    PROMOTION_URL="https://ossrh-staging-api.central.sonatype.com/service/local/staging/bulk/promote"
    JSON_DATA="{
        \"stagedRepositoryIds\": [\"${deployed_staging_repo_id}\"], 
        \"autoDropAfterRelease\": true, 
        \"description\": \"Releasing ${deployed_staging_repo_id}\"}
      }"
      
    RESPONSE_CODE=$(curl -o /tmp/out.txt -w "%{http_code}\n" -X POST "${PROMOTION_URL}" \
      -u "${SONATYPE_USERNAME}:${SONATYPE_PASSWORD}" \
      -H "Content-Type: application/json" \
      -H "Accept: application/json" \
      -d "{\"data\": ${JSON_DATA}}")
    
    if [[ ${RESPONSE_CODE} != 200 ]]; then
        echo "Failed to close and release staging repository ${deployed_staging_repo_id}. Response code: ${RESPONSE_CODE}"
        echo "Response: $(cat /tmp/out.txt)"
        echo "Please release the staging repository manually via Sonatype portal https://central.sonatype.com/publishing ."
    else
        echo "Staging repository ${deployed_staging_repo_id} released successfully."
    fi

    echo "==========================================="
    echo "Done."
    echo "==========================================="
fi