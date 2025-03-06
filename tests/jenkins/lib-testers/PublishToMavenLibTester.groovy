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

class PublishToMavenLibTester extends LibFunctionTester {

    private String signingArtifactsPath
    private String mavenArtifactsPath
    private String autoPublish

    public PublishToMavenLibTester(signingPath, artifactPath, autoPub) {
        this.signingArtifactsPath = signingPath
        this.mavenArtifactsPath = artifactPath
        this.autoPublish = autoPub
    }

    @Override
    void configure(helper, binding) {
        binding.setVariable('SONATYPE_USERNAME', 'sonatype_user')
        binding.setVariable('SONATYPE_PASSWORD', 'sonatype_pass')
        binding.setVariable('GITHUB_BOT_TOKEN_NAME', 'github_bot_token_name')
        binding.setVariable('WORKSPACE', 'workspace')
        helper.registerAllowedMethod('git', [Map])
        helper.addFileExistsMock('workspace/sign.sh', true)
        helper.registerAllowedMethod('withCredentials', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
    }

    @Override
    void parameterInvariantsAssertions(call) {
        assertThat(call.args.signingArtifactsPath.first(), notNullValue())
        assertThat(call.args.mavenArtifactsPath.first(), notNullValue())
        assertThat(call.args.autoPublish.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(call) {
        return call.args.signingArtifactsPath.first().equals(this.signingArtifactsPath)
    }

    String libFunctionName() {
        return 'publishToMaven'
    }
}
