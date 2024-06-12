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

class CloseBuildSuccessGithubIssueLibTester extends LibFunctionTester{
    private List<String> message
    private String search
    private String inputManifestPath

    public CloseBuildSuccessGithubIssueLibTester(message, search, inputManifestPath){
        this.message = message
        this.search = search
        this.inputManifestPath = inputManifestPath
    }

    @Override
    String libFunctionName() {
        return 'closeBuildSuccessGithubIssue'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.message.first(), notNullValue())
        assertThat(call.args.search.first(), notNullValue())
        assertThat(call.args.inputManifestPath.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.message.first().equals(this.message) && call.args.search.first().equals(this.search) && call.args.inputManifestPath.first().equals(this.inputManifestPath)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod('withCredentials', [Map])
        helper.registerAllowedMethod('sleep', [Map])
        binding.setVariable('BUILD_URL', 'www.example.com/jobs/test/123/')
    }
}
