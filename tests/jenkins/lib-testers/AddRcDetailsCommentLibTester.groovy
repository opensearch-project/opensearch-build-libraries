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
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat

class AddRcDetailsCommentLibTester extends LibFunctionTester{
    private String version
    private Integer opensearchRcNumber = 5
    private Integer opensearchDashboardsRcNumber = 5

    public AddRcDetailsCommentLibTester(version){
        this.version = version
    }

    public AddRcDetailsCommentLibTester(version, opensearchRcNumber, opensearchDashboardsRcNumber){
        this.version = version
        this.opensearchRcNumber = opensearchRcNumber
        this.opensearchDashboardsRcNumber = opensearchDashboardsRcNumber
    }

    @Override
    String libFunctionName() {
        return 'addRcDetailsComment'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.version.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.version.first().equals(this.version)
    }

    @Override
    void configure(Object helper, Object binding) {}
}
