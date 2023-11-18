/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

 /* Thanks to the jenkins community https://community.jenkins.io/t/jenkins-dsl-get-stage-logs-as-list-and-print-the-failures/6111/3
* Library to get logs of a specific stage in jenkins
* @param args.stageName <required> - Name of the stage to get logs. Returns a List<String>
*/
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.jenkinsci.plugins.workflow.actions.LabelAction
import org.jenkinsci.plugins.workflow.actions.LogAction
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import java.util.stream.Collectors

@NonCPS
def call(Map args = [:]) {
    if (args.stageName == null || args.stageName.allWhitespace || args.stageName.isEmpty()) {
        error('stageName cannot be emppty. Please provide one.')
    }
    List<String> stageLogs = collectLogsForStage(args.stageName)
}
// Recursively check flowNode parents until we find a stage
@NonCPS
String getFlowNodeStage(FlowNode flowNode) {
    for (FlowNode parent : flowNode.getParents()) {
        if (parent instanceof StepStartNode && isNamedStageStartNode(parent)) {
            return parent.getAction(LabelAction.class).getDisplayName()
        } else {
            return getFlowNodeStage(parent)
        }
    }
    // Return null if no stage found. Null will be passed through all recursion levels
    return null
}

// Collect logs of each flow node that belongs to stage
@NonCPS
List<String> collectLogsForStage(String stageName) {
    currentBuild.rawBuild.save()
    List<String> logs = []
    DepthFirstScanner scanner = new DepthFirstScanner()

    scanner.setup(currentBuild.rawBuild.getExecution().getCurrentHeads())

    for (FlowNode flowNode : scanner) {
        // Skip flow nodes that are not part of a requested stage
        // If stage not found for the current flow node, getFlowNodeStage() will return null
        if(stageName.equals(getFlowNodeStage(flowNode))) {
            LogAction logAction = flowNode.getAction(LogAction.class)
            if (logAction != null) {
                def reader = new BufferedReader(logAction.getLogText().readAll())
                List<String> flowNodeLogs = reader.lines().collect(Collectors.toList())
                logs.addAll(0, flowNodeLogs)
            }
        }
    }
    return logs
}

@NonCPS
private boolean isNamedStageStartNode(FlowNode node) {
    return Objects.equals(((StepStartNode) node).getStepName(), "Stage") && !Objects.equals(node.getDisplayFunctionName(), "stage");
}