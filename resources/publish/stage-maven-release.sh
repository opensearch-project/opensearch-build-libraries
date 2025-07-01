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
#                all artifacts in the passed in directory. If auto_publish is enabled, 
#                it will publish to maven central. The folder passed as input should contain 
#                subfolders org/opensearch to ensure artifacts are deployed under the correct groupId.
#                Example: ./stage-maven-release.sh /maven
#                - where maven contains /maven/org/opensearch
#
# Usage:         ./stage-maven-release.sh <directory> -a <true|false>
#
###############################################################################################
set -e

usage() {
  echo "usage: $0 [-h] [dir] -a <true|false>"
  echo "  dir     parent directory containing artifacts to org/opensearch namespace."
  echo "          example: dir = ~/.m2/repository where repository contains /org/opensearch"
  echo "  -h      display help"
  echo "  -a      auto-publish to maven central after staging repository is created. Defaults to false."
  echo "Required environment variables:"
  echo "SONATYPE_USERNAME - username with publish rights to a sonatype repository"
  echo "SONATYPE_PASSWORD - password for sonatype"
  echo "JOB_NAME - Job Name which triggered this script for tracking purposes"
  echo "BUILD_ID - Build ID from CI so we can trace where the artifacts were built"
  echo "STAGING_PROFILE_ID - Sonatype Staging profile ID"
  exit 1
}

[ -z "${1:-}" ] && {
  usage
  exit 1
}

while getopts ":ha:" option; do
  case $option in
  h)
    usage
    ;;
  a)
    auto_publish="${OPTARG:-false}"
    ;;
  \?)
    echo "Invalid option -$OPTARG" >&2
    usage
    exit 1
    ;;
  esac
done

[ -z "${SONATYPE_USERNAME}" ] && {
  echo "SONATYPE_USERNAME is required"
  exit 1
}

[ -z "${SONATYPE_PASSWORD}" ] && {
  echo "SONATYPE_PASSWORD is required"
  exit 1
}

[ -z "${JOB_NAME}" ] && {
  echo "JOB_NAME is required"
  exit 1
}

[ -z "${BUILD_ID}" ] && {
  echo "BUILD_ID is required"
  exit 1
}

[ -z "${STAGING_PROFILE_ID}" ] && {
  echo "STAGING_PROFILE_ID is required"
  exit 1
}

if [ ! -d "$1" ]; then
  echo "Invalid directory $1 does not exist"
  usage
fi

[ ! -d "$1"/org/opensearch ] && {
  echo "Given directory does not contain opensearch artifacts"
  usage
}

staged_repo=$1

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

function create_staging_repository() {
  echo "Creating staging repository."
  staging_repo_id=$(mvn --settings="${mvn_settings}" \
    org.sonatype.plugins:nexus-staging-maven-plugin:rc-open \
    -DnexusUrl="https://ossrh-staging-api.central.sonatype.com" \
    -DserverId=central \
    -DstagingProfileId="${STAGING_PROFILE_ID}" \
    -DstagingDescription="Staging artifacts for ${JOB_NAME}-${BUILD_ID}" \
    -DopenedRepositoryMessageFormat="opensearch-staging-repo-id=%s" |
    grep -E -o 'opensearch-staging-repo-id=.*$' | cut -d'=' -f2)
  echo "Opened staging repository ID $staging_repo_id"
}

create_maven_settings
create_staging_repository

echo "==========================================="
echo "Deploying artifacts under ${staged_repo} to Staging Repository ${staging_repo_id}."
echo "==========================================="

mvn --settings="${mvn_settings}" \
  org.sonatype.plugins:nexus-staging-maven-plugin:1.6.13:deploy-staged-repository \
  -DrepositoryDirectory="${staged_repo}" \
  -DnexusUrl="https://ossrh-staging-api.central.sonatype.com" \
  -DserverId=central \
  -DautoReleaseAfterClose=false \
  -DstagingProgressTimeoutMinutes=30 \
  -DstagingProfileId="${STAGING_PROFILE_ID}"

echo "==========================================="
echo "Done."
echo "==========================================="

# If auto_publish is set to true below commands will be executed See https://github.com/sonatype/nexus-maven-plugins/blob/main/staging/maven-plugin/README.md
# for command reference.
if [ "$auto_publish" = true ] ; then
    export MAVEN_OPTS=--add-opens=java.base/java.util=ALL-UNNAMED

    echo "==========================================="
    echo "Closing Staging Repository ${staging_repo_id}."
    echo "==========================================="

    mvn --settings="${mvn_settings}" \
      org.sonatype.plugins:nexus-staging-maven-plugin:1.6.13:rc-close \
      -DnexusUrl="https://ossrh-staging-api.central.sonatype.com" \
      -DserverId=central \
      -DautoReleaseAfterClose=true \
      -DstagingProfileId="${STAGING_PROFILE_ID}" \
      -DstagingRepositoryId="${staging_repo_id}"

    echo "==========================================="
    echo "Done."
    echo "==========================================="

    echo "==========================================="
    echo "Release Staging Repository ${staging_repo_id}."
    echo "==========================================="

    mvn --settings="${mvn_settings}" \
      org.sonatype.plugins:nexus-staging-maven-plugin:1.6.13:rc-release \
      -DnexusUrl="https://ossrh-staging-api.central.sonatype.com" \
      -DserverId=central \
      -DstagingProfileId="${STAGING_PROFILE_ID}" \
      -DstagingRepositoryId="${staging_repo_id}"

    echo "==========================================="
    echo "Done."
    echo "==========================================="
fi
