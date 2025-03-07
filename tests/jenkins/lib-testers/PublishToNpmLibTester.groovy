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
import static org.hamcrest.MatcherAssert.assertThat

class PublishToNpmLibTester extends LibFunctionTester {

    private String publicationType
    private String artifactPath = ''
    private List allowedpubs = ['github', 'artifact']

    public PublishToNpmLibTester(publicationType) {
        this.publicationType = publicationType
    }

    public PublishToNpmLibTester(publicationType, artifactPath) {
        this.publicationType = publicationType
        this.artifactPath = artifactPath
    }

    void configure(helper, binding) {
        helper.registerAllowedMethod("checkout", [Map], {})
        helper.registerAllowedMethod("withCredentials", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
    }
    void parameterInvariantsAssertions(call) {
        assertThat(call.args.publicationType.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return allowedpubs.contains(call.args.publicationType.first().toString())
    }

    String libFunctionName() {
        return 'publishToNpm'
    }
}
