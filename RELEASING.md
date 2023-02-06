- [Overview](#overview)
- [Versioning](#versioning)
- [Releasing](#releasing)

## Overview

This document explains the release strategy for artifacts in this organization.

## Versioning

This respository, as other in this organization follows semantic versioning.

- **major**: Breaking changes
- **minor**: New features
- **patch**: Bug fixes

## Releasing

The release process includes a [maintainer](MAINTAINERS.md) voluntering for the release. They need to follow the below steps:
* Changing the version number in [build.gradle](https://github.com/opensearch-project/opensearch-build-libraries/blob/main/build.gradle#L123). 
* This triggers the [version increment workflow](.github/workflows/version-increment.yml) and creates a version increment PR across the 1.x branch. [Example](https://github.com/gaiksaya/opensearch-build-libraries-1/pull/1)  
* Once merged, the maintainer needs to push a tag based on 1.x which creates a release on GitHub via [release.yml](./.github/workflows/release.yml) workflow.
