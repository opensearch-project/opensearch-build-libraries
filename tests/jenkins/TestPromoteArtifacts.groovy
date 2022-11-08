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
import java.util.*
import java.nio.file.*

class TestPromoteArtifacts extends BuildPipelineTest {
    private Path targetOpenSearchLinuxTar;
    private Path targetOpenSearchDashboardsLinuxTar;
    private Path targetOpenSearchLinuxTarQualifier;
    private Path targetOpenSearchDashboardsLinuxTarQualifier;
    private Path targetOpenSearchLinuxRpm;
    private Path targetOpenSearchDashboardsLinuxRpm;
    private Path targetOpenSearchLinuxRpmQualifier;
    private Path targetOpenSearchDashboardsLinuxRpmQualifier;
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
        binding.setVariable('DISTRIBUTION_PLATFORM', 'linux')
        binding.setVariable('DISTRIBUTION_ARCHITECTURE', 'x64')
        binding.setVariable('WORKSPACE', 'tests/jenkins')
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
        helper.addFileExistsMock('tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch/core-plugins', true)

        helper.addShMock('find tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch/core-plugins -type f') { script ->
            return [stdout: "tar_dummy_artifact_1.3.0.tar.gz zip_dummy_artifact_1.3.0.zip dummy_artifact_1.3.0.dummy", exitValue: 0]
        }
        helper.addShMock('sha512sum tar_dummy_artifact_1.3.0.tar.gz') { script ->
            return [stdout: "shaHashDummy_tar_dummy_artifact_1.3.0.tar.gz  tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch/core-plugins/tar_dummy_artifact_1.3.0.tar.gz", exitValue: 0]
        }
        helper.addShMock('sha512sum zip_dummy_artifact_1.3.0.zip') { script ->
            return [stdout: "shaHashDummy_zip_dummy_artifact_1.3.0.zip  tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch/core-plugins/zip_dummy_artifact_1.3.0.zip", exitValue: 0]
        }
        helper.addShMock('basename tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch/core-plugins/tar_dummy_artifact_1.3.0.tar.gz') { script ->
            return [stdout: "tar_dummy_artifact_1.3.0.tar.gz", exitValue: 0]
        }
        helper.addShMock('basename tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch/core-plugins/zip_dummy_artifact_1.3.0.zip') { script ->
            return [stdout: "zip_dummy_artifact_1.3.0.zip", exitValue: 0]
        }

        targetOpenSearchLinuxTar = copy(
            "tests/data/opensearch-build-1.3.0.yml", 
            "tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch/manifest.yml"
        );

        targetOpenSearchDashboardsLinuxTar = copy(
            "tests/data/opensearch-dashboards-build-1.3.0.yml", 
            "tests/jenkins/artifacts/tar/vars-build/1.3.0/33/linux/x64/tar/builds/opensearch-dashboards/manifest.yml"
        );

        targetOpenSearchLinuxTarQualifier = copy(
            "tests/data/opensearch-build-2.0.0-rc1.yml", 
            "tests/jenkins/artifacts/tar/vars-build/2.0.0-rc1/33/linux/x64/tar/builds/opensearch/manifest.yml"
        );

        targetOpenSearchDashboardsLinuxTarQualifier = copy(
            "tests/data/opensearch-dashboards-build-2.0.0-rc1.yml", 
            "tests/jenkins/artifacts/tar/vars-build/2.0.0-rc1/33/linux/x64/tar/builds/opensearch-dashboards/manifest.yml"
        );

        targetOpenSearchLinuxRpm = copy(
            "tests/data/opensearch-build-1.3.0-rpm.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/1.3.0/33/linux/x64/rpm/builds/opensearch/manifest.yml"
        );

        targetOpenSearchDashboardsLinuxRpm = copy(
            "tests/data/opensearch-dashboards-build-1.3.0-rpm.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/1.3.0/33/linux/x64/rpm/builds/opensearch-dashboards/manifest.yml"
        );

        targetOpenSearchLinuxRpmQualifier = copy(
            "tests/data/opensearch-build-2.0.0-rc1-rpm.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/2.0.0-rc1/33/linux/x64/rpm/builds/opensearch/manifest.yml"
        );

        targetOpenSearchDashboardsLinuxRpmQualifier = copy(
            "tests/data/opensearch-dashboards-build-2.0.0-rc1-rpm.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/2.0.0-rc1/33/linux/x64/rpm/builds/opensearch-dashboards/manifest.yml"
        );
        targetOpenSearchWindowsZip = copy(
            "tests/data/opensearch-build-1.3.0-windows-zip.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch/manifest.yml"
        );
        targetOpenSearchDashboardsWindowsZip = copy(
            "tests/data/opensearch-dashboards-build-1.3.0-windows-zip.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/1.3.0/33/windows/x64/zip/builds/opensearch-dashboards/manifest.yml"
        );
        targetOpenSearchWindowsZipQualifier = copy(
            "tests/data/opensearch-build-2.0.0-rc1-windows-zip.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/2.0.0-rc1/33/windows/x64/zip/builds/opensearch/manifest.yml"
        );
        targetOpenSearchDashboardsWindowsZipQualifier = copy(
            "tests/data/opensearch-dashboards-build-2.0.0-rc1-windows-zip.yml", 
            "tests/jenkins/artifacts/rpm/vars-build/2.0.0-rc1/33/windows/x64/zip/builds/opensearch-dashboards/manifest.yml"
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
        Files.delete(targetOpenSearchLinuxTar)
        Files.delete(targetOpenSearchDashboardsLinuxTar)
        Files.delete(targetOpenSearchLinuxTarQualifier)
        Files.delete(targetOpenSearchDashboardsLinuxTarQualifier)
        Files.delete(targetOpenSearchLinuxRpm)
        Files.delete(targetOpenSearchDashboardsLinuxRpm)
        Files.delete(targetOpenSearchLinuxRpmQualifier)
        Files.delete(targetOpenSearchDashboardsLinuxRpmQualifier)
        Files.delete(targetOpenSearchWindowsZip)
        Files.delete(targetOpenSearchDashboardsWindowsZip)
        Files.delete(targetOpenSearchWindowsZipQualifier)
        Files.delete(targetOpenSearchDashboardsWindowsZipQualifier)
    }

    @Test
    public void testDefault() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_Jenkinsfile")
    }

    @Test
    public void testDefault_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_OpenSearch_Dashboards_Jenkinsfile")
    }

    @Test
    public void testDefaultQualifier() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_Jenkinsfile")
    }

    @Test
    public void testDefaultQualifier_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_OpenSearch_Dashboards_Jenkinsfile")
    }

    @Test
    public void testWithActions() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_actions_Jenkinsfile")
    }

    @Test
    public void testWithActions_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifacts_actions_OpenSearch_Dashboards_Jenkinsfile")
    }

    @Test
    public void testWithActionsQualifier() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_actions_Jenkinsfile")
    }

    @Test
    public void testWithActionsQualifier_OpenSearch_Dashboards() {
        super.testPipeline("tests/jenkins/jobs/PromoteArtifactsQualifier_actions_OpenSearch_Dashboards_Jenkinsfile")
    }
}
