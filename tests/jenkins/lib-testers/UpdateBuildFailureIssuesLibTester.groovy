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

class UpdateBuildFailureIssuesLibTester extends LibFunctionTester{
    private List<String> failureMessages
    private List<String> passMessages
    private String inputManifestPath

    public UpdateBuildFailureIssuesLibTester(failureMessages, passMessages, inputManifestPath){
        this.failureMessages = failureMessages
        this.passMessages = passMessages
        this.inputManifestPath = inputManifestPath
    }

    @Override
    String libFunctionName() {
        return 'UpdateBuildFailureIssues'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.failureMessages.first(), notNullValue())
        assertThat(call.args.passMessages.first(), notNullValue())
        assertThat(call.args.inputManifestPath.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.failureMessages.first().equals(this.failureMessages)
        && call.args.passMessages.first().equals(this.passMessages)
        && call.args.inputManifestPath.first().equals(this.inputManifestPath)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod('withCredentials', [Map])
        helper.registerAllowedMethod('sleep', [Map])
        binding.setVariable('env', ['RUN_DISPLAY_URL': 'www.example.com/job/build_url/32/display/redirect'])
    }
}
