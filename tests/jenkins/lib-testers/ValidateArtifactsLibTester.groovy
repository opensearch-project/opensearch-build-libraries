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

class ValidateArtifactsLibTester extends LibFunctionTester {

    private String version
    private String distribution
    private String arch
    private String platform
    private String projects

    public ValidateArtifactsLibTester(version, distribution, arch, platform, projects) {
        this.version = version
        this.distribution = distribution
        this.arch = arch
        this.platform = platform
        this.projects = projects

    }

    void configure(helper, binding) {
        helper.addFileExistsMock('/tmp/workspace/validation.sh', true)
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.version.first(), notNullValue())
        assertThat(call.args.projects.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.version.first().toString().equals(this.version)
            && call.args.projects.first().toString().equals(this.projects)
    }

    String libFunctionName() {
        return 'validateArtifacts'
    }

}
