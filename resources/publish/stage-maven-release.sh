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
# About:         Deploy opensearch artifacts to maven central.
#                This script will zip all artifacts in the passed in directory into a bundle and
#                upload it to the Sonatype Central Publisher Portal, then wait for validation.
#                If AUTO_PUBLISH is enabled, it will publish to maven central once validated, and wait until published.
#                The folder passed as input should contain subfolders org/opensearch
#                to ensure artifacts are deployed under the correct groupId.
#                Example: ./stage-maven-release.sh -d /maven -a false
#                - where maven contains /maven/org/opensearch
#
# Usage:         ./stage-maven-release.sh -d <directory> -a <true|false> [-n <upload_name>]
#
###############################################################################################
set -e

usage() {
  echo "usage: $0 [-h] -d <path_to_artifacts_dir> -a <true|false> [-n <upload_name>]"
  echo "  -h      display help"
  echo "  -d      parent directory containing artifacts to org/opensearch namespace."
  echo "          example: dir = ~/.m2/repository where repository contains /org/opensearch"
  echo "  -a      auto-publish to maven central after validation passes. Defaults to false."
  echo "  -n      upload zip name without .zip. Defaults to maven_upload."
  echo "Required environment variables:"
  echo "SONATYPE_USERNAME - username with publish rights to a sonatype repository"
  echo "SONATYPE_PASSWORD - publishing token for sonatype"
  exit 1
}
AUTO_PUBLISH=false
UPLOAD_NAME="maven_upload"
CENTRAL_PORTAL_URL="https://central.sonatype.com"

while getopts ":ha:d:n:" option; do
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
  n)
    UPLOAD_NAME="${OPTARG}"
    ;;
  \?)
    echo "Invalid option -$OPTARG" >&2
    usage
    ;;
  :)
    echo "Option -$OPTARG requires an argument" >&2
    usage
    ;;
  esac
done

if [ "$AUTO_PUBLISH" != "true" ] && [ "$AUTO_PUBLISH" != "false" ]; then
  echo "Error: Invalid value for -a: '$AUTO_PUBLISH'. Must be 'true' or 'false'"
  usage
fi

required_env_vars=(ARTIFACT_DIRECTORY SONATYPE_USERNAME SONATYPE_PASSWORD)
for var in "${required_env_vars[@]}"; do
  if [ -z "${!var}" ]; then
    echo "Error: $var is required"
    usage
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

cleanup() {
  rm -rf "${workdir}"
}
trap cleanup TERM INT EXIT

echo "AUTO_PUBLISH variable is set to: '$AUTO_PUBLISH'"
echo "==========================================="
echo "Deploying artifacts under ${ARTIFACT_DIRECTORY} to Central Publisher Portal."
echo "==========================================="

# Since 2026-10, Central Publisher Portal validation fails with a misleading "Bundle has content that does NOT have a .pom file"
# when the bundle contains maven-metadata*.xml files, even though the poms are present.
# https://github.com/opensearch-project/opensearch-build-libraries/issues/944, https://community.sonatype.com/t/central-portal-rejects-a-valid-bundle-bundle-has-content-that-does-not-have-a-pom-file-poms-are-present/16708
echo "Removing maven-metadata files so that Central Publisher Portal will not reject bundles"
find "${ARTIFACT_DIRECTORY}" -name 'maven-metadata*.xml*' -delete

SONATYPE_AUTH="Authorization: Bearer $(printf '%s:%s' "${SONATYPE_USERNAME}" "${SONATYPE_PASSWORD}" | base64 | tr -d '\n')"
if [ "${AUTO_PUBLISH}" = true ]; then
  PUBLISHING_TYPE="AUTOMATIC"
  EXPECTED_STATE="PUBLISHED"
  EXPECTED_MESSAGE="Deployment published to Maven Central."
else
  PUBLISHING_TYPE="USER_MANAGED"
  EXPECTED_STATE="VALIDATED"
  EXPECTED_MESSAGE="Deployment validated, please publish it manually via Sonatype portal ${CENTRAL_PORTAL_URL}/publishing ."
fi

bundle="${workdir}/${UPLOAD_NAME}.zip"
(cd "${ARTIFACT_DIRECTORY}" && zip -qr "${bundle}" .)
echo "Uploading ${UPLOAD_NAME}.zip ($(du -h "${bundle}" | cut -f1)) with publishingType=${PUBLISHING_TYPE} at $(date -u)."
RESPONSE_CODE=$(curl -s -o "${workdir}/upload_response" -w "%{http_code}" --max-time 1800 -X POST -H "${SONATYPE_AUTH}" \
  --form bundle=@"${bundle}" "${CENTRAL_PORTAL_URL}/api/v1/publisher/upload?publishingType=${PUBLISHING_TYPE}" || true)
echo "Upload response code: ${RESPONSE_CODE} at $(date -u)."

if [[ ${RESPONSE_CODE} != 2* ]]; then
  echo "Failed to upload bundle. Response code: ${RESPONSE_CODE}"
  echo "Please check the Sonatype portal ${CENTRAL_PORTAL_URL}/publishing ."
  exit 1
fi
PORTAL_DEPLOYMENT_ID=$(cat "${workdir}/upload_response")
if [ -z "${PORTAL_DEPLOYMENT_ID}" ]; then
  echo "Upload returned no deployment ID, please check via Sonatype portal ${CENTRAL_PORTAL_URL}/publishing ."
  exit 1
fi

# Wait for the Portal validation, and the publish when AUTO_PUBLISH=true
MAX_STATUS_CHECKS=60
for i in $(seq 1 "${MAX_STATUS_CHECKS}"); do
  DEPLOYMENT_STATUS=$(curl -s --max-time 30 -X POST -H "${SONATYPE_AUTH}" "${CENTRAL_PORTAL_URL}/api/v1/publisher/status?id=${PORTAL_DEPLOYMENT_ID}" || true)
  DEPLOYMENT_STATE=$(echo "${DEPLOYMENT_STATUS}" | jq -R -r 'fromjson? | .deploymentState // empty')
  echo "Deployment state: ${DEPLOYMENT_STATE:-no response or invalid data} (${i}/${MAX_STATUS_CHECKS})."
  if [ "${DEPLOYMENT_STATE}" = "FAILED" ]; then
    DEPLOYMENT_ERRORS=$(echo "${DEPLOYMENT_STATUS}" | jq -R -r 'fromjson? | .errors // empty')
    echo "==========================================="
    echo "Deployment failed with errors:"
    echo "${DEPLOYMENT_ERRORS}"
    echo "==========================================="
    echo "Please check the errors via Sonatype portal ${CENTRAL_PORTAL_URL}/publishing ."
    exit 1
  fi
  if [ "${DEPLOYMENT_STATE}" = "${EXPECTED_STATE}" ]; then
    break
  fi
  if [ "${i}" -eq "${MAX_STATUS_CHECKS}" ]; then
    echo "Timed out waiting for the deployment, please check via Sonatype portal ${CENTRAL_PORTAL_URL}/publishing ."
    exit 1
  fi
  sleep 30
done

echo "${EXPECTED_MESSAGE}"

echo "==========================================="
echo "Done."
echo "==========================================="
