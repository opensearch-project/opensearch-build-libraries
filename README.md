<img src="https://opensearch.org/assets/brand/SVG/Logo/opensearch_logo_default.svg" height="64px"/>

[![groovy](https://github.com/opensearch-project/opensearch-build-libraries/actions/workflows/groovy-tests.yml/badge.svg)](https://github.com/opensearch-project/opensearch-build-libraries/actions/workflows/groovy-tests.yml)
[![codecov](https://codecov.io/gh/opensearch-project/opensearch-build-libraries/branch/main/graph/badge.svg)](https://codecov.io/gh/opensearch-project/opensearch-build-libraries)

- [OpenSearch Build Libraries](#opensearch-build-libraries)
    - [Jenkins Shared Libraries](#jenkins-shared-libraries)
- [Contributing](#contributing)
- [Getting Help](#getting-help)
- [Code of Conduct](#code-of-conduct)
- [Security](#security)
- [License](#license)
- [Copyright](#copyright)

## OpenSearch Build Libraries
OpenSearch build libraries consist of shared libraries used to build, test and release OpenSearch, OpenSearch Dashboards and its associated products such as clients, drivers, etc.

### Jenkins Shared Libraries
This repository consist of jenkins shared libraries as one of the libraries. Read more about jenkins shared libraries [here](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)
Checkout different [retrieval methods](https://www.jenkins.io/doc/book/pipeline/shared-libraries/#retrieval-method) that can be used by jenkins files to use this remote library.

Example:
```
lib = library(identifier: 'jenkins@<tag>', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/opensearch-project/opensearch-build-libraries.git',
]))
```

#### Library Details

| Name                                                                                                        | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|-------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [standardReleasePipeline.groovy](./vars/standardReleasePipeline.groovy)                                     | The library sets up the necessary jenkins properties for you such as agent label, docker image to use as well as workflow time out. Check how to use the [default](./tests/jenkins/jobs/StandardReleasePipeline_JenkinsFile) in your workflow and how to [overide](./tests/jenkins/jobs/StandardReleasePipelineWithArgs_JenkinsFile) agent & docker image if you need.                                                                                                                                                                      |
| [standardReleasePipelineWithGenericTrigger.groovy](./vars/standardReleasePipelineWithGenericTrigger.groovy) | A standard release pipeline for OpenSearch projects including generic triggers. A tag or a draft release can be used as a trigger using this library. The defaults are all set to trigger via a draft release. If the release is successful, the release can be published by using right params.. Check how to use the [default](./tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggers_Jenkinsfile) in your workflow and how to [overide](./tests/jenkins/jobs/StandardReleasePipelineWithGenericTriggersTag_Jenkinsfile) values. |
| [publishToNpm.groovy](./vars/publishToNpm.groovy)                                                           | A library to publish artifacts to [NPM registry](https://www.npmjs.com/) under @opensearch-project namespace. You can use [PublishToNpmLibTester](./tests/jenkins/lib-testers/PublishToNpmLibTester.groovy) to add tests in your repository. See how to use the lib in your [jenkinsFile](./tests/jenkins/jobs/PublishToNpm_Jenkinsfile).                                                                                                                                                                                                   |
| [publishToPyPi.groovy](./vars/publishToPyPi.groovy)                                                         | A library to publish artifacts to [PyPi registry](https://pypi.org/) with [OpenSearch](https://pypi.org/user/OpenSearch/) as the maintainer. This library takes care of signing the artifacts before publishing. You can use [PublishToPyPiLibTester](./tests/jenkins/lib-testers/PublishToPyPiLibTester.groovy) to add tests in your repository. See how to use the lib in your [jenkinsFile](./tests/jenkins/jobs/PublishToPyPi_Jenkinsfile).                                                                                             |
| [publishToRubyGems.groovy](./vars/publishToRubyGems.groovy)                                                 | A library to publish gems to [rubygems.org](https://rubygems.org/) with [opensearchproject](https://rubygems.org/profiles/opensearchproject) as the owner. Please note that this library expects the gems to be pre-signed. You can use [PublishToRubyGemsLibTester](./tests/jenkins/lib-testers/PublishToRubyGemsLibTester.groovy) to add tests in your repository. See how to use the lib in your [jenkinsFile](./tests/jenkins/jobs/PublishToRubyGems_JenkinsFile).                                                                 |
| [publishToMaven.groovy](./vars/publishToMaven.groovy)                                                       | A library to sign and deploy opensearch maven artifacts to sonatype staging repository, it also has an optional parameter `autoPublish` to auto-release artifacts from staging repo to prod without manual intervention. You can use [PublishToMavenLibTester](./tests/jenkins/lib-testers/PublishToMavenLibTester.groovy) to add tests in your repository. See how to use the lib in your [jenkinsFile](./tests/jenkins/jobs/PublishToMaven_JenkinsFile).                                                                                  |
| [publishToNuget.groovy](./vars/publishToNuget.groovy)                                                       | A library to build, sign and publish dotnet artifacts to [Nuget Gallery](https://www.nuget.org/). Please check if the [default docker](https://github.com/opensearch-project/opensearch-build/blob/main/docker/ci/dockerfiles/current/release.centos.clients.x64.arm64.dockerfile) file contains the required dotnet sdk. You can use [PublishToNugetLibTester](./tests/jenkins/lib-testers/PublishToNugetLibTester.groovy) to add tests in your repository. See how to use the lib in your [jenkinsFile](./tests/jenkins/jobs/PublishToNuget_Jenkinsfile).
| [publishToArtifactsProdBucket.groovy](./vars/publishToArtifactsProdBucket.groovy)                                                     | This library signs and uploads the artifacts to production S3 bucket which points to artifacts.opensearch.org. Please make sure the role that you use to upload exists and has the right permission. For artifacts of different types like macos, linux and windows, call this lib for each artifact with different signing parameters. You can use [PublishToArtifactsProdBucketLibTester](./tests/jenkins/lib-testers/PublishToArtifactsProdBucketLibTester.groovy) to add tests in your repository. See how to use the lib in your [jenkinsFile](./tests/jenkins/jobs/PublishToArtifactsProdBucket_Jenkinsfile). 
| [buildMessage.groovy](./vars/buildMessage.groovy)                                                       | This library that can parse the jenkins build log based on the user defined input query string. 
| [closeBuildSuccessGithubIssue.groovy](./vars/closeBuildSuccessGithubIssue.groovy)                                                     | This library that identifies the successfully built components and closes the created [AUTOCUT] issues.
| [createGithubIssue.groovy](./vars/createGithubIssue.groovy)                                                       | This library that identifies the failed components and creates the [AUTOCUT] issues.
| [publishGradleCheckTestResults.groovy](./vars/publishGradleCheckTestResults.groovy)                                                       | This library runs part of Gradle Check and publishes the failed test data to the [OpenSearch Metrics Cluster](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/e5e64d40-ed31-11ee-be99-69d1dbc75083).
| [gradleCheckFlakyTestDetector.groovy](./vars/gradleCheckFlakyTestDetector.groovy)                                                       | This library detects the flaky tests from [OpenSearch Metrics Cluster](https://metrics.opensearch.org/_dashboards/app/dashboards#/view/e5e64d40-ed31-11ee-be99-69d1dbc75083) and generates a test report.
| [gradleCheckFlakyTestGitHubIssue.groovy](./vars/gradleCheckFlakyTestGitHubIssue.groovy)                                                       | This library is used in [gradleCheckFlakyTestDetector.groovy](./vars/gradleCheckFlakyTestDetector.groovy) to create/edit the GitHub Issue using the generated test report.
| [publishDistributionBuildResults.groovy](./vars/publishDistributionBuildResults.groovy)                                                       | This library is used for publishing the OpenSearch Project Distribution build results to the OpenSearch Metrics cluster.
| [publishIntegTestResults.groovy](./vars/publishIntegTestResults.groovy)                                                       | This library is used for publishing the OpenSearch Project Integration Test results to the OpenSearch Metrics cluster.

## Contributing

See [developer guide](DEVELOPER_GUIDE.md) and [how to contribute to this project](CONTRIBUTING.md). 

## Getting Help

If you find a bug, or have a feature request, please don't hesitate to open an issue in this repository.

For more information, see [project website](https://opensearch.org/) and [documentation](https://docs-beta.opensearch.org/). If you need help and are unsure where to open an issue, try [forums](https://discuss.opendistrocommunity.dev/).

## Code of Conduct

This project has adopted the [Amazon Open Source Code of Conduct](CODE_OF_CONDUCT.md). For more information see the [Code of Conduct FAQ](https://aws.github.io/code-of-conduct-faq), or contact [opensource-codeofconduct@amazon.com](mailto:opensource-codeofconduct@amazon.com) with any additional questions or comments.

## Security

If you discover a potential security issue in this project we ask that you notify AWS/Amazon Security via our [vulnerability reporting page](http://aws.amazon.com/security/vulnerability-reporting/). Please do **not** create a public GitHub issue.

## License

This project is licensed under the [Apache v2.0 License](LICENSE.txt).

## Copyright

Copyright OpenSearch Contributors. See [NOTICE](NOTICE) for details.
