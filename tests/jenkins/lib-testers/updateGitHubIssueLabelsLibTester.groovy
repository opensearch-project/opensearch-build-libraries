/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat

class updateGitHubIssueLabelsLibTester extends LibFunctionTester {

    private String repoUrl
    private String issueTitle
    private String label
    private String action

    public updateGitHubIssueLabelsLibTester(repoUrl, issueTitle, label, action){
        this.repoUrl = repoUrl
        this.issueTitle = issueTitle
        this.label = label
        this.action = action
    }

    @Override
    String libFunctionName() {
        return 'updateGitHubIssueLabels'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.repoUrl.first(), notNullValue())
        assertThat(call.args.issueTitle.first(), notNullValue())
        assertThat(call.args.label.first(), notNullValue())
        assertThat(call.args.action.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.repoUrl.first().equals(this.repoUrl)
            && call.args.issueTitle.first().equals(this.issueTitle)
            && call.args.label.first().equals(this.label)
            && call.args.action.first().equals(this.action)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod('withCredentials', [Map])
    }
}
