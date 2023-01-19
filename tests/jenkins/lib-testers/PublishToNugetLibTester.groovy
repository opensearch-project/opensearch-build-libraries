/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat

class PublishToNugetLibTester extends LibFunctionTester {

    private String repository
    private String tag
    private String apiKeyCredentialId
    private String solutionFilePath

    public PublishToNugetLibTester(repository, tag, apiKeyCredentialId, solutionFilePath) {
        this.repository = repository
        this.tag = tag
        this.apiKeyCredentialId = apiKeyCredentialId
        this.solutionFilePath = solutionFilePath
    }

    void configure(helper, binding){
        helper.registerAllowedMethod("checkout", [Map], {})
        helper.registerAllowedMethod("withCredentials", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
    }
    void parameterInvariantsAssertions(call){
        assertThat(call.args.repository.first(), notNullValue())
        assertThat(call.args.tag.first(), notNullValue())
        assertThat(call.args.apiKeyCredentialId.first(), notNullValue())
        assertThat(call.args.solutionFilePath.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.repository.first().toString().equals(this.repository)
        && call.args.tag.first().toString().equals(this.tag)
        && call.args.apiKeyCredentialId.first().toString().equals(this.apiKeyCredentialId)
        && call.args.solutionFilePath.first().toString().equals(this.solutionFilePath)
    }

    String libFunctionName() {
        return 'publishToNuget'
    }
}
