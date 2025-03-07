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


class RunSmokeTestScriptLibTester extends LibFunctionTester {

    private String jobName
    private String buildManifest
    private String testManifest
    private String buildId
    private String switchUserNonRoot

    public RunSmokeTestScriptLibTester(jobName, buildManifest, testManifest, buildId, switchUserNonRoot){
        this.jobName = jobName
        this.buildManifest = buildManifest
        this.testManifest = testManifest
        this.buildId = buildId
        this.switchUserNonRoot = switchUserNonRoot
    }

    void configure(helper, binding) {
        binding.setVariable('env', ['BUILD_NUMBER': '9876'])
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.buildManifest.first().toString(), notNullValue())
        assertThat(call.args.testManifest.first().toString(), notNullValue())
        assertThat(call.args.buildId.first().toString(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.jobName.first().toString().equals(this.jobName)
                && call.args.buildManifest.first().toString().equals(this.buildManifest)
                && call.args.testManifest.first().toString().equals(this.testManifest)
                && call.args.buildId.first().toString().equals(this.buildId)
                && call.args.switchUserNonRoot.first().toString().equals(this.switchUserNonRoot)
    }

    String libFunctionName() {
        return 'runSmokeTestScript'
    }
}
