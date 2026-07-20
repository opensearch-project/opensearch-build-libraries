/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import org.junit.Before
import org.junit.Test
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat

class TestRegisterReleaseSchedule extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        super.setUp()
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
        helper.registerAllowedMethod('writeFile', [Map])
        // The exact curl command contains a generated temp filename, so match on the Map form
        // and return a 201 for any cluster call (the cleanup 'rm' return value is unused).
        helper.registerAllowedMethod('sh', [Map.class], { map -> return '201' })
    }

    @Test
    void testRegisterReleaseSchedule() {
        runScript('tests/jenkins/jobs/RegisterReleaseSchedule_Jenkinsfile')
        assertThat(getCommands('echo', 'Registered'),
                hasItem("Registered release schedule for version 3.8.0 with status 'inactive'."))
    }

    @Test
    void testRegisterReleaseScheduleWritesDocument() {
        runScript('tests/jenkins/jobs/RegisterReleaseSchedule_Jenkinsfile')
        // The schedule document is written to a temp file before the POST.
        def writes = helper.callStack.findAll { call -> call.methodName == 'writeFile' }
        assert writes.size() == 1
        String body = writes.first().args[0].text
        assert body.contains('"version":"3.8.0"')
        assert body.contains('"rc_date":"2026-08-01"')
        assert body.contains('"release_date":"2026-08-12"')
        assert body.contains('"status":"inactive"')
        assert body.contains('"registered_by":"register-release-schedule #5"')
    }

    def getCommands(String methodName, String searchString) {
        def matches = []
        helper.callStack.findAll { call -> call.methodName == methodName }.each { call ->
            def args = callArgsToString(call)
            if (args.contains(searchString)) {
                matches.add(args)
            }
        }
        return matches
    }
}
