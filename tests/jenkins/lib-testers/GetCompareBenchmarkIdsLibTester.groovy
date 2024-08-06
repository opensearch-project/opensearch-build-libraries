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

class GetCompareBenchmarkIdsLibTester extends LibFunctionTester {
    private String baselineClusterConfig
    private String distributionVersion
    private String workload
    private String pullRequestNumber

    public GetCompareBenchmarkIdsLibTester(baselineClusterConfig, distributionVersion, workload, pullRequestNumber) {
        this.baselineClusterConfig = baselineClusterConfig
        this.distributionVersion = distributionVersion
        this.workload = workload
        this.pullRequestNumber = pullRequestNumber
    }

    @Override
    String libFunctionName() {
        return 'getCompareBenchmarkIds'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        if (!this.baselineClusterConfig.isEmpty()) {
            assertThat(call.args.baselineClusterConfig.first(), notNullValue())
        }
        if (!this.distributionVersion.isEmpty()) {
            assertThat(call.args.distributionVersion.first(), notNullValue())
        }
        if (!this.workload.isEmpty()) {
            assertThat(call.args.workload.first(), notNullValue())
        }
        if (!this.pullRequestNumber.isEmpty()) {
            assertThat(call.args.pullRequestNumber.first(), notNullValue())
        }
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.pullRequestNumber.first().toString().equals(this.pullRequestNumber)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod("withCredentials", [Map])
        binding.setVariable('DATASTORE_USER', 'user')
        binding.setVariable('DATASTORE_PASSWORD', 'password')
        binding.setVariable('DATASTORE_ENDPOINT', 'endpoint')

        // Mock sh step
        helper.registerAllowedMethod("sh", [Map.class], { map ->
            return '{"hits":{"total":{"value":1},"hits":[{"_source":{"test-execution-id":"test-id"}}]}}'
        })
        helper.registerAllowedMethod("echo", [String.class], { str -> println str })
    }
}
