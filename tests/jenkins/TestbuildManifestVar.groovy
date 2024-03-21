/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import org.junit.*
import jenkins.tests.BuildPipelineTest
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat

class TestbuildManifestVar extends BuildPipelineTest {
    @Before
    void setUp() {
        super.setUp()

        binding.setVariable('JOB_NAME', 'dummy-build-job')
        binding.setVariable('PUBLIC_ARTIFACT_URL', 'dummy-url')
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("s3Download", [Map])
    }

    @Test
    void testbuildManifestWithSnapshotContinueOnError() {
        this.registerLibTester(new BuildManifestLibTester('tests/data/opensearch-2.0.0.yml', 'tar', true, true))
        super.testPipeline('tests/jenkins/jobs/BuildShManifest_Jenkinsfile')
        def shCommands = getCommands('sh', 'build.sh')
        assertThat(shCommands, hasItems('./build.sh tests/data/opensearch-2.0.0.yml -d tar --snapshot --continue-on-error'))
    }

    @Test
    void testbuildManifestWithSnapshotComponent() {
        this.registerLibTester(new BuildManifestLibTester('tests/data/opensearch-2.0.0.yml', 'tar', 'job-scheduler', true))
        super.testPipeline('tests/jenkins/jobs/BuildShManifest_Jenkinsfile')
        def shCommands = getCommands('sh', 'build.sh')
        assertThat(shCommands, hasItems('./build.sh tests/data/opensearch-2.0.0.yml -d tar --component job-scheduler --snapshot'))
    }

    @Test
    void testbuildManifestWithSnapshotComponentLock() {
        this.registerLibTester(new BuildManifestLibTester('tests/data/opensearch-2.0.0.yml', 'rpm', 'common-utils', true, true))
        super.testPipeline('tests/jenkins/jobs/BuildShManifest_Jenkinsfile')
        def shCommands = getCommands('sh', 'build.sh')
        assertThat(shCommands, hasItems('./build.sh tests/data/opensearch-2.0.0.yml -d rpm --component common-utils --snapshot --lock'))
    }

    @Test
    void testbuildManifestWithIncrementalBuild() {
        super.testPipeline('tests/jenkins/jobs/BuildShManifestIncremental_Jenkinsfile')
        def shCommands = getCommands('sh', 'build.sh')
        assertThat(shCommands, hasItems('./build.sh tests/data/opensearch-input-2.12.0.yml -d tar -p linux -a x64 --incremental'))
    }

    def getCommands(method, text) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == method
    }.collect { call ->
            callArgsToString(call)
    }.findAll { command ->
            command.contains(text)
        }
        return shCommands
    }

}

