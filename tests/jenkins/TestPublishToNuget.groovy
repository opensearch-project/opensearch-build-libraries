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
import static org.hamcrest.CoreMatchers.hasItems
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
    void verify_build_command(){
        runScript('tests/jenkins/jobs/PublishToNuget_Jenkinsfile')

        def buildCommand = getShellCommands('dotnet build')
        assertThat(buildCommand, hasItem('\n    dotnet build /tmp/workspace/test-solution-file.sln --configuration Release\n    find src/OpenSearch.*/bin/Release/*/*.dll -type f -regextype posix-extended -regex \"src/([^/]+)/bin/Release/[^/]+/\\1\\.dll\">/tmp/workspace/dlls.txt\n    '))
    }

    @Test
    void verify_pack_command(){
        runScript('tests/jenkins/jobs/PublishToNuget_Jenkinsfile')
        def packCommand = getShellCommands('pack')
        assertThat(packCommand, hasItem('\n        dotnet pack /tmp/workspace/test-solution-file.sln --configuration Release --no-build\n        find src -name OpenSearch*.nupkg > /tmp/workspace/nupkg.txt\n    '))
    }

    @Test
    void verify_signer_call(){
        runScript('tests/jenkins/jobs/PublishToNuget_Jenkinsfile')
        def signcommand = getShellCommands('sign.sh')
        assertThat(signcommand, hasItems('\n                   #!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_WINDOWS_ROLE\n                   export EXTERNAL_ID=SIGNER_WINDOWS_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_WINDOWS_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_WINDOWS_SIGNED_BUCKET\n                   export PROFILE_IDENTIFIER=SIGNER_WINDOWS_PROFILE_IDENTIFIER\n                   export PLATFORM_IDENTIFIER=SIGNER_WINDOWS_PLATFORM_IDENTIFIER\n\n                   /tmp/workspace/opensearch-build/sign.sh /tmp/workspace/one.dll --platform windows --overwrite \n               ',
        '\n                   #!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_WINDOWS_ROLE\n                   export EXTERNAL_ID=SIGNER_WINDOWS_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_WINDOWS_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_WINDOWS_SIGNED_BUCKET\n                   export PROFILE_IDENTIFIER=SIGNER_WINDOWS_PROFILE_IDENTIFIER\n                   export PLATFORM_IDENTIFIER=SIGNER_WINDOWS_PLATFORM_IDENTIFIER\n\n                   /tmp/workspace/opensearch-build/sign.sh /tmp/workspace/two.dll --platform windows --overwrite \n               ',
        '\n                   #!/bin/bash\n                   set +x\n                   export ROLE=SIGNER_WINDOWS_ROLE\n                   export EXTERNAL_ID=SIGNER_WINDOWS_EXTERNAL_ID\n                   export UNSIGNED_BUCKET=SIGNER_WINDOWS_UNSIGNED_BUCKET\n                   export SIGNED_BUCKET=SIGNER_WINDOWS_SIGNED_BUCKET\n                   export PROFILE_IDENTIFIER=SIGNER_WINDOWS_PROFILE_IDENTIFIER\n                   export PLATFORM_IDENTIFIER=SIGNER_WINDOWS_PLATFORM_IDENTIFIER\n\n                   /tmp/workspace/opensearch-build/sign.sh /tmp/workspace/three.dll --platform windows --overwrite \n               '))
    }

    @Test
    void verify_push_command(){
        runScript('tests/jenkins/jobs/PublishToNuget_Jenkinsfile')
        def pushCommand = getShellCommands('push')
        assertThat(pushCommand, hasItems(
            'dotnet nuget push /tmp/workspace/src/net/one.nupkg --api-key API_KEY --source https://api.nuget.org/v3/index.json',
            'dotnet nuget push /tmp/workspace/src/net/two.nupkg --api-key API_KEY --source https://api.nuget.org/v3/index.json',
            'dotnet nuget push /tmp/workspace/src/net/three.nupkg --api-key API_KEY --source https://api.nuget.org/v3/index.json'))
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
