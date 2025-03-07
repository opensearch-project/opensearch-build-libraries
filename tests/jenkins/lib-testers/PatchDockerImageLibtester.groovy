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
import org.yaml.snakeyaml.Yaml



class PatchDockerImageLibTester extends LibFunctionTester {

    private String product
    private String tag

    public PatchDockerImageLibTester(product, tag){
        this.product = product
        this.tag = tag
    }

    void configure(helper, binding) {
        def inputManifest = "tests/data/opensearch-1.3.0.yml"
        binding.setVariable('MANIFEST', inputManifest)

        helper.addShMock("""docker inspect --format '{{ index .Config.Labels "org.label-schema.version"}}' opensearchproject/opensearch:1""") { script ->
            return [stdout: "1.3.0", exitValue: 0]
        }
        helper.addShMock("""docker inspect --format '{{ index .Config.Labels "org.label-schema.description"}}' opensearchproject/opensearch:1""") { script ->
            return [stdout: "7756", exitValue: 0]
        }
        helper.addShMock("""date +%Y%m%d""") { script ->
            return [stdout: "20230619", exitValue: 0]
        }
        helper.addShMock("""docker inspect --format '{{ index .Config.Labels "org.label-schema.version"}}' opensearchproject/opensearch:latest""") { script ->
            return [stdout: "2.5.0", exitValue: 0]
        }
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
