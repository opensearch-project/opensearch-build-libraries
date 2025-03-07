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

class BuildManifestLibTester extends LibFunctionTester {
    private String inputManifestPath
    private String distribution = 'tar'
    private String componentName
    private Boolean snapshot = false
    private Boolean lock = false
    private Boolean continueOnError = false

    public BuildManifestLibTester(String inputManifestPath){
        this.inputManifestPath = inputManifestPath
    }

    public BuildManifestLibTester(String inputManifestPath, String distribution, Boolean snapshot){
        this.inputManifestPath = inputManifestPath
        this.distribution = distribution
        this.snapshot = snapshot
    }

    public BuildManifestLibTester(String inputManifestPath, String distribution, String componentName, Boolean snapshot){
        this.inputManifestPath = inputManifestPath
        this.distribution = distribution
        this.componentName = componentName
        this.snapshot = snapshot
    }

    public BuildManifestLibTester(String inputManifestPath, String distribution, Boolean snapshot, Boolean continueOnError){
        this.inputManifestPath = inputManifestPath
        this.distribution = distribution
        this.snapshot = snapshot
        this.continueOnError = continueOnError
    }

    public BuildManifestLibTester(String inputManifestPath, String distribution, String componentName, Boolean lock, Boolean continueOnError){
        this.inputManifestPath = inputManifestPath
        this.distribution = distribution
        this.componentName = componentName
        this.lock = lock
        this.continueOnError = continueOnError
    }

    @Override
    void configure(helper, binding) {
        helper.registerAllowedMethod("checkout", [Map], {})
    }

    @Override
    void parameterInvariantsAssertions(call) {
        assertThat(call.args.inputManifest.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(call) {
        return call.args.inputManifest.first().equals(this.inputManifestPath)
    }

    @Override
    String libFunctionName() {
    return 'buildManifest'
    }
}
