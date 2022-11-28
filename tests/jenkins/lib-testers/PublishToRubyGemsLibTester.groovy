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
import static org.hamcrest.CoreMatchers.NullValue

class PublishToRubyGemsLibTester extends LibFunctionTester {

    private String gemsDir = 'dist'
    private String certPath = 'certs/opensearch-rubygems.pem'
    private String apiKey

    public PublishToRubyGemsLibTester(String apiKey) {
        this.apiKey = apiKey
    }
    public PublishToRubyGemsLibTester(String apiKey, String gemsDir, String certPath) {
        this.apiKey = apiKey
        this.gemsDir = gemsDir
        this.certPath = certPath
    }

    void configure(helper, binding){
        helper.registerAllowedMethod("withCredentials", [Map])
    }
    void parameterInvariantsAssertions(call){
        assertThat(call.args.gemsDir.toString(), notNullValue())
        assertThat(call.args.apiKey.toString(), notNullValue())
        assertThat(call.args.certPath.toString(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.apiKey.first().toString().equals(this.apiKey)
    }

    String libFunctionName(){
        return 'publishToRubyGems'
    }

}
