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

class CloseGithubIssueLibTester extends LibFunctionTester {

    private String repoUrl
    private String issueTitle
    private String closeComment
    private String label

    public CloseGithubIssueLibTester(repoUrl, issueTitle, closeComment, label){
        this.repoUrl = repoUrl
        this.issueTitle = issueTitle
        this.closeComment = closeComment
        this.label = label
    }


    @Override
    String libFunctionName() {
        return 'closeGithubIssue'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.repoUrl.first(), notNullValue())
        assertThat(call.args.issueTitle.first(), notNullValue())
        assertThat(call.args.closeComment.first(), notNullValue())
        assertThat(call.args.label.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
            return call.args.label.first().equals(this.label)
            && call.args.repoUrl.first().equals(this.repoUrl)
            && call.args.issueTitle.first().equals(this.issueTitle)
            && call.args.closeComment.first().equals(this.closeComment)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod('withCredentials', [Map])
    }
}
