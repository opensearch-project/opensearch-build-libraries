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

class CheckDocumentationPullRequestsLibTester extends LibFunctionTester {
    private String version

    public CheckDocumentationPullRequestsLibTester(version) {
        this.version = version
    }

    @Override
    String libFunctionName() {
        return 'checkDocumentationPullRequests'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.version.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.version.first().toString().equals(this.version)
    }

    @Override
    void configure(Object helper, Object binding) {}
}

