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
import static org.hamcrest.CoreMatchers.hasItems
import static org.hamcrest.MatcherAssert.assertThat

class TestPromoteRepos extends BuildPipelineTest {

    @Override
    @Before
    void setUp() {
        this.registerLibTester(new PromoteReposLibTester('opensearch', '123', 'yum'))
        this.registerLibTester(new PromoteReposLibTester('opensearch', '123', 'apt'))
        super.setUp()

    }

    @Test
    public void test() {
        super.testPipeline("tests/jenkins/jobs/PromoteRepos_Jenkinsfile")
    }

    @Test
    void 'yum verification'() {
        runScript("tests/jenkins/jobs/PromoteRepos_Jenkinsfile")
        assertThat(getShellCommands('sh', 'curl'), hasItems('\n            set -e\n            set +x\n\n            echo \"Pulling 1.3.0 rpm\"\n            cd /tmp/workspace/artifacts/releases/bundle/opensearch/1.x/yum\n            curl -SLO https://ci.opensearch.org/dbc/opensearch/1.3.0/123/linux/x64/rpm/dist/opensearch/opensearch-1.3.0-linux-x64.rpm\n            curl -SLO https://ci.opensearch.org/dbc/opensearch/1.3.0/123/linux/arm64/rpm/dist/opensearch/opensearch-1.3.0-linux-arm64.rpm\n\n            ls -l\n        '))
        assertThat(getShellCommands('sh', 'aws'), hasItems('aws s3 sync s3://ARTIFACT_PRODUCTION_BUCKET_NAME/releases/bundle/opensearch/1.x/yum/ /tmp/workspace/artifacts/releases/bundle/opensearch/1.x/yum/ --no-progress'))
        assertThat(getShellCommands('signArtifacts', ''), hasItems('{artifactPath=/tmp/workspace/artifacts/releases/bundle/opensearch/1.x/yum/repodata/repomd.pom, sigtype=.asc, platform=linux}'))
        assertThat(getShellCommands('sh', 'repomd.pom.asc'), hasItems('\n                set -e\n                set +x\n    \n                cd /tmp/workspace/artifacts/releases/bundle/opensearch/1.x/yum/repodata/\n    \n                ls -l\n    \n                mv -v repomd.pom repomd.xml\n                mv -v repomd.pom.asc repomd.xml.asc\n    \n                ls -l\n    \n                cd -\n            '))
    }

    @Test
    void 'apt verification'() {
        runScript("tests/jenkins/jobs/PromoteRepos_Jenkinsfile")
        assertThat(getShellCommands('sh', 'curl'), hasItems('\n            set -e\n            set +x\n\n            echo \"Pulling 1.3.0 deb\"\n            cd /tmp/workspace/artifacts/releases/bundle/opensearch/1.x/apt\n            curl -SLO https://ci.opensearch.org/dbc/opensearch/1.3.0/123/linux/x64/deb/dist/opensearch/opensearch-1.3.0-linux-x64.deb\n            curl -SLO https://ci.opensearch.org/dbc/opensearch/1.3.0/123/linux/arm64/deb/dist/opensearch/opensearch-1.3.0-linux-arm64.deb\n\n            ls -l\n        '))
        assertThat(getShellCommands('sh', 'aws'), hasItems('aws s3 sync s3://ARTIFACT_PRODUCTION_BUCKET_NAME/releases/bundle/opensearch/1.x/apt/ /tmp/workspace/artifacts/releases/bundle/opensearch/1.x/apt/ --no-progress'))
        def aptly_str="#!/bin/bash\n\n                     set -e\n                     set +x\n\n                     ARTIFACT_PATH=\"/tmp/workspace/artifacts/releases/bundle/opensearch/1.x/apt\"\n\n                     echo \"Start Signing Apt\"\n                     rm -rf ~/.aptly\n                     mkdir \$ARTIFACT_PATH/base\n                     find \$ARTIFACT_PATH -type f -name \"*.deb\" | xargs -I {} mv -v {} \$ARTIFACT_PATH/base\n                     aptly repo create -comment=\"opensearch repository\" -distribution=stable -component=main opensearch\n                     aptly repo add opensearch \$ARTIFACT_PATH/base\n                     aptly repo show -with-packages opensearch\n                     aptly snapshot create opensearch-1.x from repo opensearch\n                     aptly publish snapshot -label=\"opensearch\" -origin=\"artifacts.opensearch.org\" -batch=true -passphrase-file=passphrase opensearch-1.x\n                     echo \"------------------------------------------------------------------------\"\n                     echo \"Clean up gpg\"\n                     gpg --batch --yes --delete-secret-keys RPM_SIGNING_KEY_ID\n                     gpg --batch --yes --delete-keys RPM_SIGNING_KEY_ID\n                     rm -v passphrase\n                     echo \"------------------------------------------------------------------------\"\n                     rm -rf \$ARTIFACT_PATH/*\n                     cp -rvp ~/.aptly/public/* \$ARTIFACT_PATH/\n                     ls \$ARTIFACT_PATH\n\n                "
        assertThat(getShellCommands('sh', 'aptly'), hasItems(aptly_str))
    }

    def getShellCommands(methodName, searchString) {
        def shCommands = helper.callStack.findAll { call ->
            call.methodName == methodName
        }.collect { call ->
            callArgsToString(call)
        }.findAll { command ->
            command.contains(searchString)
        }
        return shCommands
    }
}
