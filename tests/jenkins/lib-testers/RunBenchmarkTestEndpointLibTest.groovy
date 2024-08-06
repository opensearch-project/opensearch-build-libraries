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

class RunBenchmarkTestEndpointLibTester extends LibFunctionTester{

    private String command
    private String endpoint
    private String insecure
    private String workload
    private String userTag
    private String workloadParams
    private String testProcedure
    private String excludeTasks
    private String includeTasks
    private String additionalConfig
    private String telemetryParams

    public RunBenchmarkTestEndpointLibTester(command, endpoint, insecure, workload, userTag, workloadParams,
                                           testProcedure, excludeTasks, includeTasks,
                                           additionalConfig,telemetryParams){
        this.command = command
        this.endpoint = endpoint
        this.insecure = insecure
        this.workload = workload
        this.userTag = userTag
        this.workloadParams = workloadParams
        this.testProcedure = testProcedure
        this.excludeTasks = excludeTasks
        this.includeTasks = includeTasks
        this.additionalConfig = additionalConfig
        this.telemetryParams = telemetryParams
    }


    @Override
    String libFunctionName() {
        return 'runBenchmarkTestScript'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        if (!this.insecure.isEmpty()) {
            assertThat(call.args.insecure.first(), notNullValue())
        }
        if (!this.workload.isEmpty()) {
            assertThat(call.args.workload.first(), notNullValue())
        }
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.endpoint.first().toString().equals(this.endpoint)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod("s3Download", [Map])
        helper.registerAllowedMethod("uploadTestResults", [Map])
        helper.registerAllowedMethod("s3Upload", [Map])
        helper.registerAllowedMethod("withAWS", [Map, Closure], {
            args,
            closure ->
                closure.delegate = delegate
                return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('findFiles', [Map.class], null)
        helper.registerAllowedMethod("withCredentials", [Map])
        helper.registerAllowedMethod('parameterizedCron', [String], null)
        binding.setVariable('AGENT_LABEL', 'Jenkins-Agent-AL2-X64-C54xlarge-Docker-Host')
        binding.setVariable('AGENT_IMAGE', 'opensearchstaging/ci-runner:ci-runner-centos7-v1')
        binding.setVariable('ARCHITECTURE', 'x64')
        binding.setVariable('ARTIFACT_BUCKET_NAME', 'test_bucket')
        binding.setVariable('ARTIFACT_DOWNLOAD_ROLE_NAME', 'Dummy_Download_Role')
        binding.setVariable('AWS_ACCOUNT_PUBLIC', 'dummy_account')
        binding.setVariable('env', ['BUILD_NUMBER': '307'])
        binding.setVariable('BUILD_NUMBER', '307')
        binding.setVariable('BUILD_URL', 'test://artifact.url')
        binding.setVariable('COMMAND', command)
        binding.setVariable('CLUSTER_ENDPOINT', endpoint)
        binding.setVariable('GITHUB_BOT_TOKEN_NAME', 'bot_token_name')
        binding.setVariable('GITHUB_USER', 'test_user')
        binding.setVariable('GITHUB_TOKEN', 'test_token')
        binding.setVariable('USER_TAGS', userTag)
        binding.setVariable('WORKLOAD_PARAMS', workloadParams)
        binding.setVariable('TEST_PROCEDURE', testProcedure)
        binding.setVariable('EXCLUDE_TASKS', excludeTasks)
        binding.setVariable('INCLUDE_TASKS', includeTasks)
        binding.setVariable('ADDITIONAL_CONFIG', additionalConfig)
        binding.setVariable('JOB_NAME', 'benchmark-test')
        binding.setVariable('BENCHMARK_TEST_CONFIG_LOCATION', 'test_config')
        binding.setVariable('PUBLIC_ARTIFACT_URL', 'test://artifact.url')
        binding.setVariable('STAGE_NAME', 'test_stage')
        binding.setVariable('TEST_WORKLOAD', workload)
        binding.setVariable('WEBHOOK_URL', 'test://artifact.url')
        binding.setVariable('TELEMETRY_PARAMS', telemetryParams)
    }
}
