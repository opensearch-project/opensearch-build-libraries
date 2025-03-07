/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package jenkins.tests

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class TestPromoteWindowsArtifacts extends BuildPipelineTest {
    private Path targetOpenSearchWindowsZip;
    private Path targetOpenSearchDashboardsWindowsZip;
    private Path targetOpenSearchWindowsZipQualifier;
    private Path targetOpenSearchDashboardsWindowsZipQualifier;

    @Override
    @Before
    void setUp() {
        super.setUp()

        binding.setVariable('PUBLIC_ARTIFACT_URL', 'https://ci.opensearch.org/dbc')
        binding.setVariable('DISTRIBUTION_JOB_NAME', 'vars-build')
        binding.setVariable('STAGE_NAME', 'stage')
        binding.setVariable('BUILD_URL', 'http://jenkins.us-east-1.elb.amazonaws.com/job/vars/42')
        binding.setVariable('DISTRIBUTION_BUILD_NUMBER', '33')
        binding.setVariable('DISTRIBUTION_PLATFORM', 'windows')
        binding.setVariable('DISTRIBUTION_ARCHITECTURE', 'x64')
        binding.setVariable('DISTRIBUTION_NAME', 'zip')
        binding.setVariable('WORKSPACE', 'tests/jenkins')
        helper.addFileExistsMock('tests/jenkins/sign.sh', true)
        binding.setVariable('GITHUB_BOT_TOKEN_NAME', 'github_bot_token_name')
        def configs = ["role": "dummy_role",
                       "external_id": "dummy_ID",
                       "unsigned_bucket": "dummy_unsigned_bucket",
                       "signed_bucket": "dummy_signed_bucket"]
        binding.setVariable('configs', configs)
        helper.registerAllowedMethod("readJSON", [Map.class], {c -> configs})

        helper.registerAllowedMethod("git", [Map])
        helper.registerAllowedMethod("s3Download", [Map])
        helper.registerAllowedMethod("s3Upload", [Map])
        helper.registerAllowedMethod("withCredentials", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod("withAWS", [Map, Closure], { args, closure ->
            closure.delegate = delegate
            return helper.callClosure(closure)
        })
        helper.registerAllowedMethod('getPath', { args ->
            return "tests/jenkins/file/found.zip"
        })
        helper.registerAllowedMethod('findFiles', [Map], { args ->
            return [{}]
        })
        helper.addFileExistsMock('tests/jenkins/artifacts/zip/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch/core-plugins', true)

        helper.addShMock('find tests/jenkins/artifacts/zip/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch/core-plugins -type f') { script ->
            return [stdout: "zip_dummy_artifact_1.3.0.zip dummy_artifact_1.3.0.dummy", exitValue: 0]
        }
        helper.addShMock('sha512sum zip_dummy_artifact_1.3.0.zip') { script ->
            return [stdout: "shaHashDummy_zip_dummy_artifact_1.3.0.zip  tests/jenkins/artifacts/zip/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch/core-plugins/zip_dummy_artifact_1.3.0.zip", exitValue: 0]
        }
        helper.addShMock('basename tests/jenkins/artifacts/zip/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch/core-plugins/zip_dummy_artifact_1.3.0.zip') { script ->
            return [stdout: "zip_dummy_artifact_1.3.0.zip", exitValue: 0]
        }

        targetOpenSearchWindowsZip = copy(
            "tests/data/opensearch-build-1.3.0-windows-zip.yml",
            "tests/jenkins/artifacts/zip/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch/manifest.yml"
        );
        targetOpenSearchDashboardsWindowsZip = copy(
            "tests/data/opensearch-dashboards-build-1.3.0-windows-zip.yml",
            "tests/jenkins/artifacts/zip/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch-dashboards/manifest.yml"
        );
        targetOpenSearchWindowsZipQualifier = copy(
            "tests/data/opensearch-build-2.0.0-rc1-windows-zip.yml",
            "tests/jenkins/artifacts/zip/vars-build/2.0.0-rc1/33/windows/x64/zip/builds/opensearch/manifest.yml"
        );
        targetOpenSearchDashboardsWindowsZipQualifier = copy(
            "tests/data/opensearch-dashboards-build-2.0.0-rc1-windows-zip.yml",
            "tests/jenkins/artifacts/zip/vars-build/2.0.0-rc1/33/windows/x64/zip/builds/opensearch-dashboards/manifest.yml"
        );
    }

    private Path copy(String sourcePath, String targetPath){
        Path source = Path.of(sourcePath);
        Path target = Path.of(targetPath);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        return target;
    }

    @After
    void after() {
        super.setUp()
        // Test file needs to be cleaned up
        Files.delete(targetOpenSearchWindowsZip)
        Files.delete(targetOpenSearchDashboardsWindowsZip)
        Files.delete(targetOpenSearchWindowsZipQualifier)
        Files.delete(targetOpenSearchDashboardsWindowsZipQualifier)
    }

    @Test
    public void testDefault() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifacts_Jenkinsfile_Windows.txt")
    }

    @Test
    public void testDefault_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_OpenSearch_Dashboards_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifacts_OpenSearch_Dashboards_Jenkinsfile_Windows.txt")
    }

    @Test
    public void testDefaultQualifier() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifactsQualifier_Jenkinsfile_Windows.txt")
    }

    @Test
    public void testDefaultQualifier_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_OpenSearch_Dashboards_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifactsQualifier_OpenSearch_Dashboards_Jenkinsfile_Windows.txt")
    }

    @Test
    public void testWithActions() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_actions_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifacts_actions_Jenkinsfile_Windows.txt")
    }

    @Test
    public void testWithActions_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_actions_OpenSearch_Dashboards_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifacts_actions_OpenSearch_Dashboards_Jenkinsfile_Windows.txt")
    }

    @Test
    public void testWithActionsQualifier() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_actions_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifactsQualifier_actions_Jenkinsfile_Windows.txt")
    }

    @Test
    public void testWithActionsQualifier_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_actions_OpenSearch_Dashboards_Jenkinsfile",
                           "tests/jenkins/jobs/PromoteArtifactsQualifier_actions_OpenSearch_Dashboards_Jenkinsfile_Windows.txt")
    }
}
