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

class CheckReleaseOwnersLibTester extends LibFunctionTester {
    private List inputManifest
    private String action

    public CheckReleaseOwnersLibTester(inputManifest, action) {
        this.inputManifest = inputManifest
        this.action = action
    }

    @Override
    String libFunctionName() {
        return 'checkRequestAssignReleaseOwners'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.inputManifest.first(), notNullValue())
        assertThat(call.args.action.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.inputManifest.first().equals(this.inputManifest)
                && call.args.action.first().toString().equals(this.action)
    }

    @Override
    void configure(Object helper, Object binding) {}
}
