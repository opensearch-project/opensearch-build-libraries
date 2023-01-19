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
import static com.lesfurets.jenkins.unit.MethodCall.callArgsToString
import static org.hamcrest.CoreMatchers.hasItem
import static org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

class TestPublishToNuget extends BuildPipelineTest {
    @Override
    @Before
    void setUp() {

        this.registerLibTester(new PublishToNugetLibTester(
            'https://github.com/opensearch-project/opensearch-net',
            '1.2.0',
            'net-api-key',
            'test-solution-file.sln'))
        super.setUp()
    }

    @Test
    public void test() {
        super.testPipeline("tests/jenkins/jobs/PublishToNuget_Jenkinsfile")
    }

    @Test
    void 'verify build command'(){
        runScript('tests/jenkins/jobs/PublishToNuget_Jenkinsfile')

        def buildCommands = getShellCommands('build')
        assertThat(buildCommands, hasItem('\n    dotnet build /tmp/workspace/test-solution-file.sln --configuration Release\n    find src -name OpenSearch*.dll>/tmp/workspace/dlls.txt\n    '))
    }

    @Test
    void 'verify_pack_command'(){
        runScript('tests/jenkins/jobs/PublishToNuget_Jenkinsfile')
        def packCommand = getShellCommands('pack')
        assertThat(packCommand, hasItem('\n            dotnet pack /tmp/workspace/test-solution-file.sln --configuration Release --no-build\n            for package in `find src -name OpenSearch*.nupkg`\n                do\n                    dotnet nuget push $package --api-key API_KEY --source https://api.nuget.org/v3/index.json\n                done\n        '))
    }

    def getShellCommands(searchString) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == 'sh'
        }.collect { call ->
            callArgsToString(call)
        }.findAll { command ->
            command.contains(searchString)
        }
        return shCommands
    }
}
