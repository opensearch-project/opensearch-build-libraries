/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
void call(Map args = [:]) {
    def parts = [
        "${args.icon}",
        "JOB_NAME=${env.JOB_NAME}",
        "BUILD_NUMBER=[${env.BUILD_NUMBER}]",
        "MESSAGE=${args.message}",
        "BUILD_URL: ${env.BUILD_URL}",
    ]
    if (args.manifest) {
        parts.add("MANIFEST: ${args.manifest}")
    }
    parts.add(args.extra)
    text = (parts - null).join("\n")

    def secret_webhook = [
        [envVar: 'WEBHOOK_URL', secretRef: "op://opensearch-release-secrets/webhook/${args.credentialsId}"]
    ]

    withSecrets(secrets: secret_webhook){
        sh ([
            'curl',
            '-XPOST',
            '--header "Content-Type: application/json"',
            "--data '{\"result_text\":\"${text}\"}'",
            "\"${WEBHOOK_URL}\""
        ].join(' '))
    }
}
