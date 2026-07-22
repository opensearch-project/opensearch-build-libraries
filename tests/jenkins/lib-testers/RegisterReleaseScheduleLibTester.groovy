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

class RegisterReleaseScheduleLibTester extends LibFunctionTester {
    private String version
    private String rcDate
    private String releaseDate

    RegisterReleaseScheduleLibTester(version, rcDate, releaseDate) {
        this.version = version
        this.rcDate = rcDate
        this.releaseDate = releaseDate
    }

    @Override
    String libFunctionName() {
        return 'registerReleaseSchedule'
    }

    @Override
    void parameterInvariantsAssertions(Object call) {
        assertThat(call.args.version.first(), notNullValue())
    }

    @Override
    boolean expectedParametersMatcher(Object call) {
        return call.args.version.first().equals(this.version)
                && call.args.rcDate.first().equals(this.rcDate)
                && call.args.releaseDate.first().equals(this.releaseDate)
    }

    @Override
    void configure(Object helper, Object binding) {
        binding.setVariable('METRICS_HOST_ACCOUNT', 'METRICS_HOST_ACCOUNT')
        binding.setVariable('env', [
                'METRICS_HOST_URL'     : 'sample.url',
                'AWS_ACCESS_KEY_ID'    : 'abc',
                'AWS_SECRET_ACCESS_KEY': 'xyz',
                'AWS_SESSION_TOKEN'    : 'sampleToken',
                'JOB_NAME'             : 'register-release-schedule',
                'BUILD_NUMBER'         : '5'
        ])
        helper.registerAllowedMethod('withSecrets', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('withAWS', [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        // The document body is written to a temp file, then POSTed via curl -d @file.
        helper.registerAllowedMethod('writeFile', [Map])
        // The curl command contains a generated temp filename; match on the Map form and
        // return a 201 Created for any cluster call.
        helper.registerAllowedMethod('sh', [Map.class], { map -> return '201' })
    }
}
