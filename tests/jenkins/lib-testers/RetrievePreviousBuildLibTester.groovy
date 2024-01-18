/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.CoreMatchers.nullValue
import static org.hamcrest.MatcherAssert.assertThat

class RetrievePreviousBuildLibTester extends LibFunctionTester {
    private String inputManifestPath
    private String distribution = 'tar'
    private String platform = 'linux'
    private String architecture = 'tar'
    private String previousBuildId = 'latest'

    public RetrievePreviousBuildLibTester(String inputManifestPath, String platform, String architecture, String distribution, String previousBuildId){
        this.inputManifestPath = inputManifestPath
        this.platform = platform
        this.architecture = architecture
        this.distribution = distribution
        this.previousBuildId = previousBuildId
    }

    @Override
    void configure(helper, binding) {
    }

    @Override
    void parameterInvariantsAssertions(call) {
        assertThat(call.args.inputManifest.first(), notNullValue())
        assertThat(call.args.platform.first(), notNullValue())
        assertThat(call.args.architecture.first(), notNullValue())
        assertThat(call.args.distribution.first(), notNullValue())
        assertThat(call.args.previousBuildId.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(call) {
        return call.args.inputManifest.first().equals(this.inputManifestPath)
            && call.args.platform.first().equals(this.platform)
            && call.args.architecture.first().equals(this.architecture)
            && call.args.distribution.first().equals(this.distribution)
            && call.args.previousBuildId.first().equals(this.previousBuildId)
    }

    @Override
    String libFunctionName() {
        return 'retrievePreviousBuild'
    }
}
