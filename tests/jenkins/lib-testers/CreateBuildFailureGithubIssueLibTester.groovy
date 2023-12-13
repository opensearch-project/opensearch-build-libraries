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

class CreateBuildFailureGithubIssueLibTester extends LibFunctionTester{
    private List<String> message

    public CreateBuildFailureGithubIssueLibTester(message){
        this.message = message
    }

    @Override
    String libFunctionName() {
        return 'createBuildFailureGithubIssue'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.message.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.message.first().equals(this.message)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod('withCredentials', [Map])
        helper.registerAllowedMethod('sleep', [Map])
        binding.setVariable('env', ['RUN_DISPLAY_URL': 'www.example.com/job/build_url/32/display/redirect'])
    }
}
