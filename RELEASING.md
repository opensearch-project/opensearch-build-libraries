- [Overview](#overview)
- [Backporting](#backporting)
- [Versioning](#versioning)
- [Releasing](#releasing)

## Overview

This document explains the release strategy for artifacts in this organization.

## Backporting
The tags are based on major.x branches. Hence each PR needs to be backported to respective major.x branch in order to included in next release.
The Github workflow [backport.yml](.github/workflows/backport.yml) creates backport PRs automatically when the original PR with an appropriate label backport <backport-branch-name> is merged to main with the backport workflow run successfully on the PR. For example, if a PR on main needs to be backported to 1.x branch, add a label backport 1.x to the PR and make sure the backport workflow runs on the PR along with other checks. Once this PR is merged to main, the workflow will create a backport PR against 1.x branch.

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