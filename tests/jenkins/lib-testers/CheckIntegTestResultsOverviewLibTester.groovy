/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import jenkins.tests.LibFunctionTester
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat

class CheckIntegTestResultsOverviewLibTester extends LibFunctionTester {
    private List inputManifest

    public CheckIntegTestResultsOverviewLibTester(inputManifest) {
        this.inputManifest = inputManifest
    }

    @Override
    String libFunctionName() {
        return 'checkIntegTestResultsOverview'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.inputManifest.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.inputManifest.first().equals(this.inputManifest)
    }

    @Override
    void configure(Object helper, Object binding) {}
}
