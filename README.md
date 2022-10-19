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

| Name                                                   | Description                                                                              |
|--------------------------------------------------------|:-----------------------------------------------------------------------------------------|
| [standardReleasePipeline.groovy](./vars/standardReleasePipeline.groovy)  | The library sets up the necessary jenkins properties for you such as agent label, docker image to use as well as workflow time out. Check how to use the [default](./tests/jenkins/jobs/StandardReleasePipeline_JenkinsFile) in your workflow and how to [overide](./tests/jenkins/jobs/StandardReleasePipelineWithArgs_JenkinsFile) agent & docker image if you need.|

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
