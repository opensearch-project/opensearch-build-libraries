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

    private String project
    private String version
    private boolean rerelease

    public PatchDockerImageLibTester(project, version, rerelease){
        this.project = project
        this.version = version
        this.rerelease = rerelease
    }

    void configure(helper, binding) {
        def inputManifest = "tests/data/opensearch-1.3.0.yml"
        binding.setVariable('MANIFEST', inputManifest)

        helper.addReadFileMock('versionNumber', '1.3.0')
        helper.addReadFileMock('time', '2023-06-19T19:12:59Z')
        helper.addReadFileMock('buildNumber', '1880')
        helper.registerAllowedMethod('readYaml', [Map.class], { args ->
            return new Yaml().load((inputManifest as File).text)
        })
        helper.registerAllowedMethod("git", [Map])
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.project.first(), notNullValue())
        assertThat(call.args.version.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.project.first().toString().equals(this.project)
                && call.args.version.first().toString().equals(this.version)
    }

    String libFunctionName() {
        return 'patchDockerImage'
    }
}
