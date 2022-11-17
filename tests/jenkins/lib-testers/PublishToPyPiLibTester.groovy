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

class PublishToPyPiLibTester extends LibFunctionTester {

    private String artifactsPath = 'dist'

    public PublishToPyPiLibTester(){}
    public PublishToPyPiLibTester(String artifactsPath){
        this.artifactsPath = artifactsPath
    }

    void configure(helper, binding){
        binding.setVariable('GITHUB_BOT_TOKEN_NAME', 'github_bot_token_name')
        helper.registerAllowedMethod("git", [Map])
        helper.registerAllowedMethod("withCredentials", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
    }
    void parameterInvariantsAssertions(call){
        assertThat(call.args.artifactsPath.toString(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        if (call.args.artifactsPath.isEmpty()) {
            return (this.artifactsPath.equals('dist'))
        }
        return (call.args.artifactsPath.first().toString().equals(this.artifactsPath))
    }

    String libFunctionName(){
        return 'publishToPyPi'
    }

}
