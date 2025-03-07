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
import static org.hamcrest.CoreMatchers.NullValue

class PublishToPyPiLibTester extends LibFunctionTester {

    private String artifactsPath = 'dist'
    private String credentialId

    public PublishToPyPiLibTester(String credentialId) {
        this.credentialId = credentialId
    }
    public PublishToPyPiLibTester(String credentialId, String artifactsPath) {
        this.credentialId = credentialId
        this.artifactsPath = artifactsPath
    }

    void configure(helper, binding){
        binding.setVariable('GITHUB_BOT_TOKEN_NAME', 'github_bot_token_name')
        helper.registerAllowedMethod("git", [Map])
        helper.addFileExistsMock('/tmp/workspace/sign.sh', true)
        helper.registerAllowedMethod("withCredentials", [Map])
    }
    void parameterInvariantsAssertions(call){
        assertThat(call.args.artifactsPath.toString(), notNullValue())
        assertThat(call.args.credentialId.toString(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.credentialId.first().toString().equals(this.credentialId)
    }

    boolean expectedParametersMatcherArtifact(call){
        if (call.args.artifactsPath.isEmpty()) {
            return (this.artifactsPath.equals('dist'))
        }
        return (call.args.artifactsPath.first().toString().equals(this.artifactsPath))
    }

    String libFunctionName(){
        return 'publishToPyPi'
    }

}
