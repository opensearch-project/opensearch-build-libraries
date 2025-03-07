/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package jenkins.tests

import jenkins.tests.BuildPipelineTest
import org.junit.Before
import org.junit.Test
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString


class TestDownloadFromS3 extends BuildPipelineTest {

    @Before
    void setUp() {

        this.registerLibTester(new DownloadFromS3LibTester(
            'tmp-role',
            'role-credential-id',
            '/download/path',
            'dummy_bucket',
            "/tmp"
            ))
        this.registerLibTester(new DownloadFromS3LibTester(
            'tmp-role',
            'role-credential-id',
            '/download/path',
            'dummy_bucket',
            "/tmp",
            false,
            'us-west-2'
            ))

        super.setUp()
    }

    @Test
    public void testDownloadFromS3() {
        super.testPipeline('tests/jenkins/jobs/DownloadFromS3_Jenkinsfile')
    }

    @Test
    void verify_default_args(){
        runScript('tests/jenkins/jobs/DownloadFromS3_Jenkinsfile')
        def aws = getMethodCall('withAWS')
        assertThat(aws, hasItem('{role=tmp-role, roleAccount=AWS_ACCOUNT_NUMBER, duration=900, roleSessionName=jenkins-session, region=us-east-1}, groovy.lang.Closure'))
        def download = getMethodCall('s3Download')
        assertThat(download, hasItem('{file=/tmp, bucket=dummy_bucket, path=/download/path, force=false}'))
    }

    @Test
    void verify_optional_args(){
        runScript('tests/jenkins/jobs/DownloadFromS3_Jenkinsfile')
        def aws = getMethodCall('withAWS')
        assertThat(aws, hasItem('{role=tmp-role, roleAccount=AWS_ACCOUNT_NUMBER, duration=900, roleSessionName=jenkins-session, region=us-west-2}, groovy.lang.Closure'))
        def download = getMethodCall('s3Download')
        assertThat(download, hasItem('{file=/tmp, bucket=dummy_bucket, path=/download/path, force=true}'))
    }

    def getMethodCall(methodName) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == methodName
        }.collect { call ->
            callArgsToString(call)
        }
        return shCommands
    }
}
