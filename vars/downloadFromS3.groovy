/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
 /**
Library to download the artifacts from S3 bucket to local
@param Map[assumedRoleName] <Required> - Role to be assumed to download artifacts.
@param Map[roleAccountNumberCred] <Required> - AWS account number to download artifacts from.
@param Map[bucketName] <Required> -S3 bucket Name.
@param Map[downloadPath] <Required> - This is the path inside the bucket to use.
@param Map[localPath] <Required> - This is the local target file to download into.
@param Map[region] <Optional> - AWS region of the S3 bucket. Defaults to us-east-1
@param Map[force]<Optional> - Set this to true to overwrite local workspace files. Defaults to false
*/
void call(Map args = [:]) {
    boolean forceDownload = args.force ?: false
    String region = args.region ?: 'us-east-1'

    withCredentials([string(credentialsId: "${args.roleAccountNumberCred}", variable: 'AWS_ACCOUNT_NUMBER')]) {
            withAWS(role: args.assumedRoleName, roleAccount: "${AWS_ACCOUNT_NUMBER}", duration: 900, roleSessionName: 'jenkins-session', region: "${region}") {
                s3Download(file: args.localPath, bucket: args.bucketName, path: args.downloadPath, force: "${forceDownload}")
            }
    }
}
