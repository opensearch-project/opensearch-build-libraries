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

class PromoteReposLibTester extends LibFunctionTester {

    private String jobName
    private String buildNumber
    private String distributionRepoType

    public PromoteReposLibTester(jobName, buildNumber, distributionRepoType) {
        this.jobName = jobName
        this.buildNumber = buildNumber
        this.distributionRepoType = distributionRepoType
    }

    void configure(helper, binding){
        binding.setVariable('PUBLIC_ARTIFACT_URL', 'https://ci.opensearch.org/dbc')
        binding.setVariable('GITHUB_BOT_TOKEN_NAME', 'github_bot_token_name')
        def configs = ["role": "dummy_role",
                       "external_id": "dummy_ID",
                       "unsigned_bucket": "dummy_unsigned_bucket",
                       "signed_bucket": "dummy_signed_bucket"]
        binding.setVariable('configs', configs)
        helper.addFileExistsMock('/tmp/workspace/sign.sh', true)
        helper.registerAllowedMethod("readJSON", [Map.class], {c -> configs})
        helper.registerAllowedMethod("git", [Map])
        helper.registerAllowedMethod("withCredentials", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
    }

    void parameterInvariantsAssertions(call){
        assertThat(call.args.jobName.first(), notNullValue())
        assertThat(call.args.buildNumber.first(), notNullValue())
        assertThat(call.args.distributionRepoType.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.jobName.first().toString().equals(this.jobName)
                && call.args.buildNumber.first().toString().equals(this.buildNumber)
                && call.args.distributionRepoType.first().toString().equals(this.distributionRepoType)
    }

    String libFunctionName() {
        return 'promoteRepos'
    }
}
