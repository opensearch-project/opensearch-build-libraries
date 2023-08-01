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
import org.yaml.snakeyaml.Yaml



class PatchDockerImageLibTester extends LibFunctionTester {

    private String product
    private String tag
    private boolean rerelease

    public PatchDockerImageLibTester(product, tag, rerelease){
        this.product = product
        this.tag = tag
        this.rerelease = rerelease
    }

    void configure(helper, binding) {
        def inputManifest = "tests/data/opensearch-1.3.0.yml"

        helper.addReadFileMock('versionnumber', '1.3.0')
        helper.addReadFileMock('time', '2023-06-19T19:12:59Z')
        helper.addReadFileMock('number', '1880')
        helper.registerAllowedMethod('readYaml', [Map.class], { args ->
            return new Yaml().load((inputManifest as File).text)
        })
        helper.registerAllowedMethod("git", [Map])
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.product.first(), notNullValue())
        assertThat(call.args.tag.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.product.first().toString().equals(this.product)
                && call.args.tag.first().toString().equals(this.tag)
    }

    String libFunctionName() {
        return 'patchDockerImage'
    }
}
