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

class PublishToRubyGemsLibTester extends LibFunctionTester {

    private String gemsDir = 'dist'
    private String certPath = 'certs/opensearch-rubygems.pem'
    private String apiKeyCredentialId

    public PublishToRubyGemsLibTester(String apiKeyCredentialId) {
        this.apiKeyCredentialId = apiKeyCredentialId
    }
    public PublishToRubyGemsLibTester(String apiKeyCredentialId, String gemsDir, String certPath) {
        this.apiKeyCredentialId = apiKeyCredentialId
        this.gemsDir = gemsDir
        this.certPath = certPath
    }

    void configure(helper, binding){
        helper.registerAllowedMethod("withCredentials", [Map])
    }
    void parameterInvariantsAssertions(call){
        assertThat(call.args.gemsDir.toString(), notNullValue())
        assertThat(call.args.apiKeyCredentialId.toString(), notNullValue())
        assertThat(call.args.certPath.toString(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.apiKeyCredentialId.first().toString().equals(this.apiKeyCredentialId)
    }

    String libFunctionName(){
        return 'publishToRubyGems'
    }

}
