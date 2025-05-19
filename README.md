# Enterprise Pipeline Framework
[![Quality Gate Status](https://sonarqube.sys.cigna.com/api/project_badges/measure?project=enterprise-pipeline-framework&metric=alert_status)](https://sonarqube.sys.cigna.com/dashboard?id=enterprise-pipeline-framework)
[![Reliability Rating](https://sonarqube.sys.cigna.com/api/project_badges/measure?project=enterprise-pipeline-framework&metric=reliability_rating)](https://sonarqube.sys.cigna.com/dashboard?id=enterprise-pipeline-framework)
[![Security Rating](https://sonarqube.sys.cigna.com/api/project_badges/measure?project=enterprise-pipeline-framework&metric=security_rating)](https://sonarqube.sys.cigna.com/dashboard?id=enterprise-pipeline-framework)
[![Technical Debt](https://sonarqube.sys.cigna.com/api/project_badges/measure?project=enterprise-pipeline-framework&metric=sqale_index)](https://sonarqube.sys.cigna.com/dashboard?id=enterprise-pipeline-framework)
[![Vulnerabilities](https://sonarqube.sys.cigna.com/api/project_badges/measure?project=enterprise-pipeline-framework&metric=vulnerabilities)](https://sonarqube.sys.cigna.com/dashboard?id=enterprise-pipeline-framework)

## Table of Contents

- [Enterprise Pipeline Framework](#enterprise-pipeline-framework)
  - [Table of Contents](#table-of-contents)
  - [Ways to Use EPF Today](#ways-to-use-epf-today)
    - [Version and Tagging strategy](#version-and-tagging-strategy)
    - [Best Practices and Recommendations for Release and Branch Use](#best-practices-and-recommendations-for-release-and-branch-use)
  - [Integration Apps](#integration-apps)
  - [Collaboration](#collaboration)
  - [Using EPF](#using-epf)

## Ways to Use EPF Today

- Run unit and integration tests locally and upon merge requests
- Scan your code using Checkmarx and SonarQube
- Push binaries to Artifactory
- Push images to Quay or ECR
- Deploy using ArgoCd
- Deploy to OpenShift, UDeploy, or AWS

### Version and Tagging Strategy

Currently, with EPF utilizing trunk based development we typically highly discourage tracking the main
branch, as it can be considered equivalent to a nightly build. As EPF begins to rollout to teams, it is not
recommended to use `epf@<<version>>` to tie the release to a version, unless there is strong reason to do so.

EPF follows the below tagging strategy

- **Version**: Derived from version configured in pom.xml
- **release**: Recent release

EPF [CHANGELOG.md][5] contains the documentation around what changes have been made within each release.

This enables configuring their release job and callback (including candidate deployer and callback) to the same version of the library

#### Library Release Expectations

The Enterprise DevOps Team operates on a weekly release schedule (per breaking change on demand or minor change). EPF is based on a trunk release style method - this branching model implements a strategy where every new feature, bug fix or other code change is merged to one central branch in the version control system.

When a release is created, consumers of the library will see updates via the [General Enterprise DevOps Ecosystem WebEx space][4]. The message will come from the GitHub Enterprise webex bot and will inform the user community of:

1. Who created the release
2. The release tag
3. The repository (this is static and will always be enterprise-pipeline-framework)

Users of the library are encouraged to 'Watch' EPFs releases through custom watch notifications. Instructions on configuring these settings can be found [here][8].
#### Main

The main branch will hold all current in-progress features and active changes. It is typically not advised for teams to use this branch as there may be situational changes that are not fully evolved. However, as EPF begins rollout and expansion, it is recommended to use `epf@main` at this time.

## Images

[Images][6] primarily used for EPF

## Using EPF

[See Using EPF][7]

## Feedback

- Find more information on [Confluence][3]
- Ask a question in [WebEx Teams][4]
- [FAQs][1]
- [Get support] [2]

[1]: https://confluence.sys.cigna.com/x/Ex6ANg
[2]: https://confluence.sys.cigna.com/x/lw9TEw
[3]: https://confluence.sys.cigna.com/display/DvOp/Enterprise+Pipeline+Framework+%28EPF%29+-+Official
[4]: webexteams://im?space=484a8760-584e-11ec-89a0-4fa61a27f6e4
[5]: https://github.sys.cigna.com/cigna/enterprise-pipeline-framework/blob/main/CHANGELOG.md
[6]: https://confluence.sys.cigna.com/display/DvOp/EPF+Image+Inventory
[7]: https://confluence.sys.cigna.com/display/DvOp/Enterprise+Pipeline+Framework+%28EPF%29+-+Official
[8]: https://docs.github.com/en/account-and-profile/managing-subscriptions-and-notifications-on-github/setting-up-notifications/configuring-notifications#configuring-your-watch-settings-for-an-individual-repository