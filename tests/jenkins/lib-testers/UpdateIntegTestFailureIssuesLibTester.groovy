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

class UpdateIntegTestFailureIssuesLibTester extends LibFunctionTester{
    private String inputManifestPath
    private String distributionBuildNumber = '4891'

    public UpdateIntegTestFailureIssuesLibTester(inputManifestPath, distributionBuildNumber){
        this.inputManifestPath = inputManifestPath
        this.distributionBuildNumber = distributionBuildNumber
    }

    public UpdateIntegTestFailureIssuesLibTester(inputManifestPath){
        this.inputManifestPath = inputManifestPath
    }

    @Override
    String libFunctionName() {
        return 'updateIntegTestFailureIssues'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.inputManifestPath.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.inputManifestPath.first().equals(this.inputManifestPath)
    }

    @Override
    void configure(Object helper, Object binding) {}
}
