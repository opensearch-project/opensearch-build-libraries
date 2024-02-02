/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/** Library to edit GitHub issue labels across opensearch-project repositories.
 @param Map args = [:] args A map of the following parameters
 @param args.repoUrl <required> - GitHub repository URL to create issue
 @param args.issueTitle <required> - GitHub issue title
 @param args.label <required> - Comma separated values of labels to add or remove from GitHub issue
 @param args.action <required> - Either remove or add the given label(s)
 */
void call(Map args = [:]) {
    verifyActions(args.action)
    try {
        withCredentials([usernamePassword(credentialsId: 'jenkins-github-bot-token', passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'GITHUB_USER')]) {
            def issueNumber = sh(
                    script: "gh issue list --repo ${args.repoUrl} -S \"${args.issueTitle} in:title\" --json number --jq '.[0].number'",
                    returnStdout: true
            ).trim()
            if (!issueNumber.isEmpty()) {
                switch (args.action) {
                    case ("add"):
                        addAction(args, issueNumber)
                        break
                    case ("remove"):
                        removeAction(args, issueNumber)
                        break
                    default:
                        error("Invalid action '${args.action}'. Valid values: add, remove")
                }
            } else {
                println("No open issues found for ${args.repoUrl}")
            }
        }
    } catch (Exception ex) {
        error("Unable to edit GitHub issue for ${args.repoUrl}", ex.getMessage())
    }
}

def addAction(args, issueNumber) {
    List<String> allLabels = Arrays.asList(args.label.split(','))
    allLabels.each { i ->
        try {
            def name = sh(
                    script: "gh label list --repo ${args.repoUrl} -S ${i} --json name --jq '.[0].name'",
                    returnStdout: true
                ).trim()
            if (name.equals(i.trim())) {
                println("Label ${i} already exists. Adding it to the issue")
            } else {
                println("${i} label is missing. Creating the missing label")
                sh(
                    script: "gh label create ${i} --repo ${args.repoUrl}",
                    returnStdout: true
                )
            }
            println("Adding ${i} label to the issue")
            sh(
                script: "gh issue edit ${issueNumber} -R ${args.repoUrl} --add-label \"${i}\"",
                returnStdout: true
            )
        } catch (Exception ex) {
            error("Unable to create GitHub label for ${args.repoUrl}", ex.getMessage())
        }
    }
}

def removeAction(args, issueNumber){
    List<String> allLabels = Arrays.asList(args.label.split(','))
    allLabels.each { i ->
        try {
            def name = sh(
                    script: "gh label list --repo ${args.repoUrl} -S ${i} --json name --jq '.[0].name'",
                    returnStdout: true
                ).trim()
            if (name.equals(i.trim())) {
                println("Removing label ${i} from the issue")
                sh(
                    script: "gh issue edit ${issueNumber} -R ${args.repoUrl} --remove-label \"${i}\"",
                    returnStdout: true
                )
            } else {
                println("${i} label does not exist. Skipping the label removal")
            }
        } catch (Exception ex) {
            error("Unable to remove GitHub label for ${args.repoUrl}", ex.getMessage())
        }
    }
}

def verifyActions(String action) {
    acceptableActions = ['add', 'remove']
    if (!acceptableActions.contains(action)){
        error("Invalid action '${action}' specified. Valid values: add, remove")
    }
}
