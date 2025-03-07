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

class RunIntegTestScriptForOSDLibTester extends LibFunctionTester {

    private String localComponent
    private String ciGroup
    private String switchUserNonRoot
    private String artifactPathOpenSearch
    private String artifactBucketName
    private String artifactPath
    private String distribution
    private String buildManifest
    private String testManifest

    public RunIntegTestScriptForOSDLibTester(localComponent, ciGroup, switchUserNonRoot, artifactPathOpenSearch, artifactBucketName,
                                             artifactPath, distribution, buildManifest, testManifest) {
        this.localComponent = localComponent
        this.ciGroup = ciGroup
        this.switchUserNonRoot = switchUserNonRoot
        this.artifactPathOpenSearch = artifactPathOpenSearch
        this.artifactBucketName = artifactBucketName
        this.artifactPath = artifactPath
        this.distribution = distribution
        this.buildManifest = buildManifest
        this.testManifest = testManifest
    }

    @Override
    String libFunctionName() {
        return 'runIntegTestScriptForOSD'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.localComponent.first(), notNullValue())
        assertThat(call.args.switchUserNonRoot.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.localComponent.first().toString().equals(this.localComponent)
                && call.args.switchUserNonRoot.first().toString().equals(this.switchUserNonRoot)
    }

    @Override
    void configure(Object helper, Object binding) {
        helper.registerAllowedMethod('downloadFromS3', [Map.class], { args -> return null })
        helper.registerAllowedMethod('runIntegTestScript', [Map.class], { args -> return null })
        helper.registerAllowedMethod('unstash', [String.class], { name -> return null })
        helper.registerAllowedMethod("withAWS", [Map, Closure], {
            args,
            closure ->
                closure.delegate = delegate
                return helper.callClosure(closure)
        })
        binding.setVariable('BUILD_NUMBER', '307')
        binding.setVariable('BUILD_JOB_NAME', 'dummy-integ-test')
        binding.setVariable('WORKSPACE', '/home/user')
    }
}
