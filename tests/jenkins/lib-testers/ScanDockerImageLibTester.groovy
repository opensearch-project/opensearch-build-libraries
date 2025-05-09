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


class ScanDockerImageLibTester extends LibFunctionTester {

    private String imageFullName
    private String imageResultFile

    public ScanDockerImageLibTester(imageFullName, imageResultFile){
        this.imageFullName = imageFullName
        this.imageResultFile = imageResultFile
    }

    void configure(helper, binding) {
        // N/A
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.imageFullName.first(), notNullValue())
        assertThat(call.args.imageResultFile.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.imageFullName.first().toString().equals(this.imageFullName)
                && call.args.imageResultFile.first().toString().equals(this.imageResultFile)
    }

    String libFunctionName() {
        return 'scanDockerImage'
    }
}
