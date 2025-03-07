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


class CreateUploadTestReportManifestLibTester extends LibFunctionTester {

    private String testType
    private String componentName
    private String buildManifest
    private String testManifest
    private String buildManifestDashboards
    private String testRunID

    public CreateUploadTestReportManifestLibTester(testManifest, buildManifest, buildManifestDashboards, testRunID, testType, componentName){
        this.testManifest = testManifest
        this.buildManifest = buildManifest
        this.buildManifestDashboards = buildManifestDashboards
        this.testRunID = testRunID
        this.testType = testType
        this.componentName = componentName
    }

    void configure(helper, binding) {
        binding.setVariable('env', ['JOB_NAME': 'dummy_integ_test', 'BUILD_NUMBER': '487', 'PUBLIC_ARTIFACT_URL': 'DUMMY_PUBLIC_ARTIFACT_URL' ])
        helper.registerAllowedMethod("withCredentials", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("s3Upload", [Map])
    }

    void parameterInvariantsAssertions(call) {
        assertThat(call.args.testManifest.first(), notNullValue())
        assertThat(call.args.buildManifest.first(), notNullValue())
        assertThat(call.args.testRunID.first(), notNullValue())
        assertThat(call.args.testType.first(), notNullValue())
    }

    boolean expectedParametersMatcher(call) {
        return call.args.testManifest.first().toString().equals(this.testManifest)
                && call.args.buildManifest.first().toString().equals(this.buildManifest)
                && call.args.testRunID.first().toString().equals(this.testRunID)
                && call.args.testType.first().toString().equals(this.testType)
    }

    String libFunctionName() {
        return 'createUploadTestReportManifest'
    }
}
