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

class CheckReleaseNotesLibTester extends LibFunctionTester{
    private String version
    private String dataTable
    private String action = 'check'

    public CheckReleaseNotesLibTester(version, dataTable, action){
        this.version = version
        this.dataTable = dataTable
        this.action = action
    }

    @Override
    String libFunctionName() {
        return 'checkReleaseNotes'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.version.first(), notNullValue())
        assertThat(call.args.dataTable.first(), notNullValue())
        assertThat(call.args.action.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.version.first().equals(this.version)
                && call.args.dataTable.first().equals(this.dataTable)
                && call.args.action.first().toString().equals(this.action)
    }

    @Override
    void configure(Object helper, Object binding) {}
}

