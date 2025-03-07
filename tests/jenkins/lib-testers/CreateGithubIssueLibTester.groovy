/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat

class CreateGithubIssueLibTester extends LibFunctionTester {

    private String repoUrl
    private String issueTitle
    private String issueBody
    private String label
    private String daysToReOpen

    public CreateGithubIssueLibTester(repoUrl, issueTitle, issueBody, label){
        this.repoUrl = repoUrl
        this.issueTitle = issueTitle
        this.issueBody = issueBody
        this.label = label
    }

    public CreateGithubIssueLibTester(repoUrl, issueTitle, issueBody){
        this.repoUrl = repoUrl
        this.issueTitle = issueTitle
        this.issueBody = issueBody
    }

    public CreateGithubIssueLibTester(repoUrl, issueTitle, issueBody, label, daysToReOpen){
        this.repoUrl = repoUrl
        this.issueTitle = issueTitle
        this.issueBody = issueBody
        this.label = label
        this.daysToReOpen = daysToReOpen
    }

    @Override
    String libFunctionName() {
        return 'createGithubIssue'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.repoUrl.first(), notNullValue())
        assertThat(call.args.issueTitle.first(), notNullValue())
        assertThat(call.args.issueBody.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        if (call.args.label.isEmpty()) {
            return call.args.label.first().equals('autocut')
                    && call.args.repoUrl.first().equals(this.repoUrl)
                    && call.args.issueTitle.first().equals(this.issueTitle)
                    && call.args.issueBody.first().equals(this.issueBody)}
        if (call.args.daysToReOpen.isEmpty()) {
            return call.args.daysToReOpen.first().equals('3')
                    && call.args.repoUrl.first().equals(this.repoUrl)
                    && call.args.issueTitle.first().equals(this.issueTitle)
                    && call.args.issueBody.first().equals(this.issueBody)}
        return call.args.repoUrl.first().equals(this.repoUrl)
                && call.args.issueTitle.first().equals(this.issueTitle)
                && call.args.issueBody.first().equals(this.issueBody)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod('withCredentials', [Map])
    }
}
