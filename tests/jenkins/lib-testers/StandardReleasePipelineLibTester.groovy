/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat

class StandardReleasePipelineLibTester extends LibFunctionTester {
    private String overrideDockerImage
    private String overrideAgent

    public StandardReleasePipelineLibTester(overrideAgent, overrideDockerImage){
        this.overrideDockerImage = overrideDockerImage
        this.overrideAgent = overrideAgent
    }

    void configure(helper, binding) {
    }

    void parameterInvariantsAssertions(call){
        assertThat(call.args.overrideDockerImage.first(), notNullValue())
        assertThat(call.args.overrideAgent.first(), notNullValue())
        assertThat(call.closure.body, notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.overrideDockerImage.first().equals(this.overrideDockerImage)
                && call.args.overrideAgent.first().equals(this.overrideAgent)
    }

    String libFunctionName() {
        return 'standardReleasePipeline'
    }
}
