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

class BuildMessageLibTester extends LibFunctionTester {

    private String search

    public BuildMessageLibTester(search) {
        this.search = search
    }


     void parameterInvariantsAssertions(call) {
        assertThat(call.args.search.first(), notNullValue())
     }

     boolean expectedParametersMatcher(call) {
        return call.args.search.first().toString().equals(this.search)
     }

    String libFunctionName() {
        return 'buildMessage'
    }

    void configure(helper, binding){
    }
}
