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
import jenkins.tests.BuildPipelineTest
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.core.IsNot.not

class TestRetrievePreviousBuild extends BuildPipelineTest {
    @Before
    void setUp() {
        super.setUp()

        binding.setVariable('JOB_NAME', 'dummy_job')
        this.registerLibTester(new RetrievePreviousBuildLibTester('tests/data/opensearch-input-2.12.0.yml', 'linux', 'x64', 'tar', '123'))
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("s3Download", [Map])
    }

    @Test
    void testRetrievePreviousBuild() {
        super.testPipeline('tests/jenkins/jobs/RetrievePreviousBuild_Jenkinsfile')
        def shCommands = getCommands('sh', 'mkdir')
        assertThat(shCommands, hasItems('rm -rf tar && mkdir -p tar && mv -v /tmp/workspace/download/dummy_job/2.12.0/123/linux/x64/tar/* /tmp/workspace/tar'))
        assertThat(shCommands, hasItems('mkdir -p $HOME/.m2/repository/org/ && cp -r tar/builds/opensearch/maven/org/opensearch/ $HOME/.m2/repository/org/'))

        assertThat(shCommands, hasItems('rm -rf zip && mkdir -p zip && mv -v /tmp/workspace/download/dummy_job/2.12.0/1234/windows/x64/zip/* /tmp/workspace/zip'))
        assertThat(shCommands, not(hasItems('mkdir -p ~/.m2/repository/org/ && cp -r zip/builds/opensearch/maven/org/opensearch/ ~/.m2/repository/org/')))

        assertThat(shCommands, hasItems('rm -rf tar && mkdir -p tar && mv -v /tmp/workspace/download/dummy_job/3.0.0-alpha1/123123/linux/x64/tar/* /tmp/workspace/tar'))
        assertThat(shCommands, not(hasItems('mkdir -p ~/.m2/repository/org/ && cp -r tar/builds/opensearch/maven/org/opensearch/ ~/.m2/repository/org/')))

        def s3DownloadCommands = getCommands('s3Download', 'bucket').findAll {
            shCommand -> shCommand.contains('bucket')
        }

        assertThat(s3DownloadCommands, hasItems(
                "{file=/tmp/workspace/download, bucket=ARTIFACT_BUCKET_NAME, path=dummy_job/2.12.0/123/linux/x64/tar/, force=true}".toString()))

        assertThat(s3DownloadCommands, hasItems(
                "{file=/tmp/workspace/download, bucket=ARTIFACT_BUCKET_NAME, path=dummy_job/2.12.0/1234/windows/x64/zip/, force=true}".toString()))
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

