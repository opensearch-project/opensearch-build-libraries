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

class ValidateArtifactsLibTester extends LibFunctionTester {

    private String version
    private String project

    public ValidateArtifactsLibTester(version, project) {
        this.version = version
        this.project = project
    }

    void configure(helper, binding) {
        helper.addFileExistsMock('/tmp/workspace/validation.sh', true)
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.version.first(), notNullValue())
        assertThat(call.args.project.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.version.first().toString().equals(this.version)
            && call.args.project.first().toString().equals(this.project)
    }

    String libFunctionName() {
        return 'validateArtifacts'
    }

}
