# Changelog
***** PLEASE RECORD ALL CHANGELOG ITEM WITH THE ASSOCIATED JIRA STORY ******

All notable changes to this project will be documented in this file.
Note: The content enclosed in set of predefined markers will be published a release notes for every release. The content between the marker should be a valid json string value.
For any parse error, a default note will be published which should be manually updated

The format is based on [Keep a Changelog][1].
This project adheres to [Semantic Versioning][2].

Please structure bullets as follows:
- \<IssueLink> \<Action> \<Class> to do a thing

For example:
- **Change** MavenBuild to add additional command arguments

[//]: # (- START TEMPLATE --)
[//]: # (## [2.5.3] - 2024-08-19)
[//]: # (- **Add** ability to publish only snapshot versions in Gradle build)
[//]: # (-- END TEMPLATE --)

## [2.9.37] - 2025-05-16
- [CNPT-2324](https://jira.express-scripts.com/browse/CNPT-2324) - **Bump** sonar image to update certs

## [2.9.36] - 2025-05-15
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Update** podman-aws image to update certs and minor version upgrades

## [2.9.35] - 2025-05-14
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Change** certfix repo path to reflect updates to the repo

## [2.9.34] - 2025-05-14
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Bump** kaniko image to update certs & version from 1.6.0 to 1.16.0

## [2.9.33] - 2025-05-13
- [CNPT-2324](https://jira.express-scripts.com/browse/CNPT-2324) - **Bump** opa, cypress, argocd-cli and oc-cli images to update certs

## [2.9.32] - 2025-05-14
- [CNPT-2330](https://jira.express-scripts.com/browse/CNPT-2330) - **Bump** ACE image to update certs
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2345) - **Bump** DotNetCore image to update certs and SDK to 8
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2346) - **Bump** Maven image to update certs
- [CNPT-2349](https://jira.express-scripts.com/browse/CNPT-2349) - **Bump** JFrog image to update certs
- [CNPT-2324](https://jira.express-scripts.com/browse/CNPT-2324) - **Bump** python, jmeter, newman and helm-cli image tags to update certs

## [2.9.31] - 2025-05-13
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Bump** maven and gradle module images to update certs

## [2.9.30] - 2025-05-12
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Bump** JNLP and helm image tags to update certs 

## [2.9.29] - 2025-05-09
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Bump** maven and gradle module images to update certs

## [2.9.28] - 2025-05-09
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Bump** node, aws, and terraform images to update certs

## [2.9.27] - 2025-05-08
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Bump** k8s, podman, and ansible images to update certs

## [2.9.26] - 2025-05-07
- [CNPT-2329](https://jira.express-scripts.com/browse/CNPT-2329) - **Bump** cnp-docker-core and cnp-docker-go images to update certs

## [2.9.25] - 2025-05-02
- [CNPT-2306](https://jira.express-scripts.com/browse/CNPT-2306) - **Remove** executor data for checkmarx and sonarqube goals from Compliance Validator, as they have been removed from compliance check

## [2.9.24] - 2025-05-02
- Bump the version of the cnp-docker-k8s image to 1.2.2 to include the tagCommitAs configuration element.

## [2.9.23] - 2025-05-01
- [CNPT-2300](https://jira.express-scripts.com/browse/CNPT-2300) - **Add** per-pod namespace quota reporting

## [2.9.22] - 2025-04-30
- [CNPT-2053](https://jira.express-scripts.com/browse/CNPT-2053) - **Fix** QEAPythonRoboTest testType to run 2 different containers in the same pod

## [2.9.21] - 2025-04-29
- [CNPT-2298](https://jira.express-scripts.com/browse/CNPT-2298) - **Remove** unused/obsolete/abandoned DSL methods from vars

## [2.9.20] - 2025-04-24
- [CNPT-2251](https://jira.express-scripts.com/browse/CNPT-2251) - **Add** internal switch for skipping stashing on single pods (no behavioral changes expected)

## [2.9.19] - 2025-04-23
- [CNPT-2235](https://jira.express-scripts.com/browse/CNPT-2235) - Bump cnp-docker-k8s image version to:
  - **Add** logging chart version
  - **Remove** obsolete local template from cnp-deploy-argorollouts module

## [2.9.18] - 2025-04-22
- **Revert** changes from version 2.9.17

## [2.9.17] - 2025-04-22
- [CNPT-1845](https://jira.express-scripts.com/browse/CNPT-1845) **Rename** main to develop, use main as tag for "prod environment"

## [2.9.16] - 2025-04-22
- [CNPT-2287](https://jira.express-scripts.com/browse/CNPT-2287) **Add** phase's display name to generic failure message
  - when a phase fails with the message 'script returned exit code X', the error message displayed will provide more info as to where
   in the pipeline the failure happened

## [2.9.15] - 2025-04-21
- [CNPT-2286](https://jira.express-scripts.com/browse/CNPT-2286) **Add** outcome message to splunk logging in more cases

## [2.9.14] - 2025-04-17
- **Remove** privileged security context when service account is set (no behavioral changes expected)

## [2.9.13] - 2025-04-15
- **Remove** obsolete parameter `inBetweenPhaseCache` (no behavioral changes expected)

## [2.9.12] - 2025-04-11
- [CNPT-2242](https://jira.express-scripts.com/browse/CNPT-2242) **Add** phases and failure message to data sent to Splunk

## [2.9.11] - 2025-04-10
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180)
  - **Add** processing module results file regardless of exit code

## [2.9.10] - 2025-04-09
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180)
  - **Fix** ability to use pipeline-helm module in callback jobs
  - **Fix** inconsistent stash naming between "base pipelines" and callback jobs

## [2.9.9] - 2025-04-08
- [FEDAD-2527](https://jira.express-scripts.com/browse/FEDAD-2527)
  - **Fix** the error highlighting by clearly presenting the actual error messages from module instead of "script returned exit code 1" to better assist with troubleshooting. Bumped docker core and terraform defualt images.

## [2.9.8] - 2025-04-08
- [CNPT-2087](https://jira.express-scripts.com/browse/CNPT-2087) - **Remove** hard-coded registry URLs to enable EPF to leverage the search list feature implemented in openshift and EKS for high availability of EPF images
<br>Ref: https://confluence.sys.cigna.com/x/17swEw (For Openshift) and https://confluence.sys.cigna.com/display/DvOp/EKS+Jenkins+Gatekeeper+Repository+Search+List (For EKS)

## [2.9.7] - 2025-04-04
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180)
  - **Add** ability to specify closures to run before and after phases (mostly for internal use)
  - **Refactor** phase list serialization to be more general and robust

## [2.9.6] - 2025-04-03
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180)
  - **Refactor** runPhases method to be more widely available (expecting no noticeable change in behavior)

## [2.9.5] - 2025-04-03
- [CNPT-2243](https://jira.express-scripts.com/browse/CNPT-2243)
  - **Fix** container selection for sonar scans on .NET codebases
  - **Add** commonArgs parameter for .NET builds
  - **Deprecate** sonarScanner parameter in favor of clearer name, useDotNetScanner 

## [2.9.4] - 2025-04-01 
- [CNPT-2244](https://jira.express-scripts.com/browse/CNPT-2244) **Add** optional parameter to IBM ACE Build to exclude default flags in build steps

## [2.9.3] - 2025-03-31
- [FEDAD-2230](https://jira.express-scripts.com/browse/CNPT-2230) - **Deprecate** kaniko phase in favor of podman (re-adding changes from 2.9.1)

## [2.9.2] - 2025-03-31
- **Revert** 2.9.1 and 2.9.0 due to a stashing issue in 2.9.0 - this change effectively resets the codebase to 2.8.6

## [2.9.1] - 2025-03-28
- [FEDAD-2230](https://jira.express-scripts.com/browse/CNPT-2230) - **Update** Deprecate kaniko phase in favor of podman

## [2.9.0] - 2025-03-28
- [CNPT-2179](https://jira.express-scripts.com/browse/CNPT-2179) - **Refactor** phase execution routine to be accessible outside cignaBuildFlow
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180) - **Change** Prerelease phase's release package retrieval to use phase execution routine
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180) - **Add** flag for phase execution to choose whether to check out code
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180) - **Fix** stash name normalization across phase execution routines
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180) - **Fix** module status handling to be based on results file first, then exit code
- [CNPT-2180](https://jira.express-scripts.com/browse/CNPT-2180) - **Fix** pipeline-helm module default settings to work in callback jobs

## [2.8.6] - 2025-03-21
- **Fix** defect when debug logging with parallel phases

## [2.8.5] - 2025-03-17
- **Fix** a possible stack overflow scenario in logging from ConsoleLogger

## [2.8.4] - 2025-03-12
- **Refactor** some duplicated code around pipeline metadata entries
- **Remove** redundant assignment/debug logging for pipeline metadata that hasn't changed

## [2.8.3] - 2025-03-11
- [CNPT-2191](https://jira.express-scripts.com/browse/CNPT-2191) - **Change** ACE Build to use most recent version of ACE. Add dependency override so that teams with monorepo style ACE pipelines can succeed.
  
## [2.8.2] - 2025-03-10
- [CNPT-2199](https://jira.express-scripts.com/browse/CNPT-2199) - **Remove** quay scan

## [2.8.1] - 2025-03-10
- [CNPT-2155](https://jira.express-scripts.com/browse/CNPT-2155) - **Add** new pipeline-helm module and bump default k8s image
tag for rendering helm templates and pushing them to git (with no direct interaction with k8s/ArgoCD)

## [2.8.0] - 2025-03-07
- [CNPT-2115](https://jira.express-scripts.com/browse/CNPT-2115) - **Add** capability to specify 'tasks' on a ServiceNow standard change ticket, which are created when standard change ticket is created, and must be completed before ticketed deployment occurs

## [2.7.26] - 2025-03-06 
- [CNPT-2206](https://jira.express-scripts.com/browse/CNPT-2206) - **bug fix** Module arg.credentials with custom variable names will be propagated to callback jobs

## [2.7.25] - 2025-03-04
- [CNPT-2057](https://jira.express-scripts.com/browse/CNPT-2057) - Bump podman image to fix the logic in cnptools image builder which was not checking full version of the tag before skipping it.

## [2.7.24] - 2025-03-04
- [CNPT-2199](https://jira.express-scripts.com/browse/CNPT-2199) - **Remove** quay endorse
- [CNPT-2197](https://jira.express-scripts.com/browse/CNPT-2197) - Bump podman image to cleanup image signing from build and publish image in tools

## [2.7.23] - 2024-03-03
- QEANeoload Test
  - **Add** resiliency testing parameters
  - **Add** `reportOnly` parameter for generating only report for a past test. Pass in `testId` to get the report.
  - **Add** code for creating a virtual environment using `requirements.txt` checked out from  [EPT_Utilities](https://github.sys.cigna.com/cigna/EPT_Utilities) repo.
  - **Change** smbclient to custom python module for NAS Transfer.
  - **Remove** `nlRegion` option - the neoload tests will only run on sass. dev or prod is not available anymore.

## [2.7.22] - 2025-02-27
- Bump podman image to remove image signing from build and publish image

## [2.7.21] - 2025-02-14
- [CNPT-2126](https://jira.express-scripts.com/browse/CNPT-2126) - **Remove** go module subcommand changes for 'build' and 'test' to enable ability to use for integration testing.

## [2.7.20] - 2025-02-14
- [BTT-4919](https://jira.express-scripts.com/browse/BTT-4919) - **Remove** unnecessary space from regex in QEATestingUtils searchxml method

## [2.7.19] - 2025-02-12
- [CNPT-2049](https://jira.express-scripts.com/browse/CNPT-2049) - **Change** error stack trace printing to be more consistent and readable

## [2.7.18] - 2025-02-12
- [CNPT-2149](https://jira.express-scripts.com/browse/CNPT-2149) - **Change** Enhanced to access repos from GH Cloud (GHC)

## [2.7.17] - 2025-02-04
- [CNPT-2176](https://jira.express-scripts.com/browse/CNPT-2176) - **Update** - Bump default tag for java & gradle image to 1.2.8 to upgrade to a checkmarx plugin version compatible with both HS and Enterprise servers (1.1.33) 
- Revert changes from PR https://github.sys.cigna.com/cigna/cnp-java/pull/45, https://github.sys.cigna.com/cigna/cnp-java/pull/47 and https://github.sys.cigna.com/cigna/cnp-java/pull/48 to rollback artifactPath change

## [2.7.16] - 2025-01-31
- [CNPT-1392](https://jira.express-scripts.com/browse/CNPT-1392) - **Update** - Bump default tag for gradle java image to 1.2.7 to include changes from PR https://github.sys.cigna.com/cigna/cnp-java/pull/45 update artifactPath

## [2.7.15] - 2025-01-31
- [CNPT-2169](https://jira.express-scripts.com/browse/CNPT-2169) - **Update** - Bump default tag for java image to 1.2.7 to upgrade to latest version of checkmarx plugin (1.1.36)

## [2.7.14] - 2025-01-30
- Bump default image for checkmarx to 1.2.1 to upgrade to latest version of checkmarx plugin (1.1.36)

## [2.7.13] - 2025-01-29
- [CNPT-2163](https://jira.express-scripts.com/browse/CNPT-2163) - **bug fix** Some Pod Templates are not calling adhoc-perms as their command

## [2.7.12] - 2025-01-27
- [CNPT-2097](https://jira.express-scripts.com/browse/CNPT-2097) - **Change** Refactor pod templates to reduce duplication

## [2.7.11] - 2025-1-29
- [CNPT-2049](https://jira.express-scripts.com/browse/CNPT-2049) - Clean up `ConsoleLogger`

## [2.7.10] - 2025-01-28
- [CNPT-2146](https://jira.express-scripts.com/browse/CNPT-2146) - Bump default tag for java image to 1.2.4 to update default java version to 11 and maven 3.9.9 which includes default conf location to be /opt/maven/apache-maven-3.9.9/conf/ instead of usr/share/maven/conf

## [2.7.9] - 2025-01-24
- [CNPT-1684](https://jira.express-scripts.com/browse/CNPT-1684) - Bump default tag for podman to 1.1.4 to include changes from https://github.sys.cigna.com/cigna/cnp-tools/pull/34

## [2.7.8] - 2025-01-24
- Bump default tag for core to 1.1.7 which includes the following changes
  - [CNPT-2091](https://jira.express-scripts.com/browse/CNPT-2119) **bug fix** - remove unnecessary ownership change to deployment package
  - [CNPT-2121](https://jira.express-scripts.com/browse/CNPT-2121) **bug fix** - fix tagging bug in finalizeRelease

## [2.7.7] - 2025-01-22
- Bump default tag for maven to 1.2.0

## [2.7.6] - 2025-01-22
- No functional changes for consumers
- [CNPT-2091](https://jira.express-scripts.com/browse/CNPT-2091) - **Refactor** methods to make them more widely available (continued)
- [CNPT-2091](https://jira.express-scripts.com/browse/CNPT-2091) - **Refactor** in Compliance Validator to improve encapsulation

## [2.7.5] - 2025-01-08
- [CNPT-2060](https://jira.express-scripts.com/browse/CNPT-2034) - **bug fix** -  update the regex to consider all jobfolder names in artifactory repo path calculation by excluding orchestrators-folders, pilot-folders, Non-Production, Production

## [2.7.4] - 2024-12-05
- No functional changes for consumers
- [CNPT-2091](https://jira.express-scripts.com/browse/CNPT-2091) - **Refactor** methods to make them more widely available
- Fix some long-standing issues with tests accessing private fields

## [2.7.3] - 2024-12-03
- [CNPT-2105](https://jira.express-scripts.com/browse/CNPT-2105) - **Bug Fix** - Utilize pod template command that correctly sets the SIGTERM exit status for sidecar containers resulting in epf-operator being able to determine pipeline pod FAILED vs pipeline pod terminated. 

## [2.7.2] - 2024-12-03
- [CNPT-2117](https://jira.express-scripts.com/browse/CNPT-2117) - **Bug Fix** - Update the Pod Recommendation to take account of absent/null target, [upper|lower]Bounds.

## [2.7.1] - 2024-12-03
- **Bug Fix** - Update cloudkit tag to `plz-2-docker` for kaniko packaging - kaniko packaging phase uses docker for ECR authentication.

## [2.7.0] - 2024-11-22
- [CNPT-2091](https://jira.express-scripts.com/browse/CNPT-2091) - **Refactor** several methods to make them more widely available
#### Functional changes
- `resourceStrategy` is now never honored at phase-level, only at pipeline-level (was always documented as such)
- the requirement that either `gitlabConnectionName` or `githubConnectionName` be provided has been relaxed

## [2.6.36] - 2024-11-21
- *Update* all megatainer image dependencies to use cloudkitv2 instead.

## [2.6.35] - 2024-11-21
- [CNPT-1837](https://jira.express-scripts.com/browse/CNPT-1837) - **Update** go image to use latest version of go, include FIPS support and to use go modules from esi/cigna github repos via git

## [2.6.34] - 2024-11-18
- **Bug Fix** - Add `--force` to `saml2aws login` calls to avoid any usage of stale credentials

## [2.6.33] - 2024-11-14
- [CNPT-2092](https://jira.express-scripts.com/browse/CNPT-2092) - **Change** container commands to ensure all containers (main/sidecar) properly collect their exit status and exit gracefully

## [2.6.32] - 2024-11-13
- [CNPT-2075](https://jira.express-scripts.com/browse/CNPT-2075) - **bug fix** - fix approvals to honor same settings for pre/release phases as others.

## [2.6.31] - 2024-11-08
- [CIPGSSPSE-230](https://jira.express-scripts.com/browse/CIPGSSPSE-230) - **Bug Fix** Update the default Checkmarx-Toolshack image to addres the bug when checkmarx vulnerability thresholds are exceeded, it doesn't fail the pipeline in the EPF phase

## [2.6.30] - 2024-10-30
- [CNPT-2060](https://jira.express-scripts.com/browse/CNPT-2060) - **Move** - Feature Flag methods to FeatureFlags class

## [2.6.29] - 2024-10-28
- [CNPT-2072](https://jira.express-scripts.com/browse/CNPT-2072) - **Bug Fix** - Update case where CBF doesn't use groupBy but has nested phases that must run in same pod group

## [2.6.28] - 2024-10-25
- [CNPT-2058](https://jira.express-scripts.com/browse/CNPT-2058) - **Move** - Compliance messages to constants on Compliance Validator class

## [2.6.27] - 2024-10-24
- [CNPT-2060](https://jira.express-scripts.com/browse/CNPT-2060) - **Remove** extraneous uses of several config properties (no functional impact) 

## [2.6.26] - 2024-10-24
- [CNPT-2006](https://jira.express-scripts.com/browse/CNPT-2006) - **Bug Fix** - updated nested pod detection logic to use the namespace rather than the podGroup

## [2.6.25] - 2024-10-22
- [CNPT-1935](https://jira.express-scripts.com/browse/CNPT-1931) - **Update** make EPF phase-level emails also send when the phase fails

## [2.6.24] - 2024-10-22
- [CNPT-2060](https://jira.express-scripts.com/browse/CNPT-2060) - **Remove** extraneous uses of 'contextName' property (no functional impact)

## [2.6.23] - 2024-10-22
- [CNPT-2071](https://jira.express-scripts.com/browse/CNPT-2071) - **Fix** Module phase to not shadow the declaration of psc from CignaBuildFlowPipelineLib

## [2.6.22] - 2024-10-21
- [CNPT-2065](https://jira.express-scripts.com/browse/CNPT-2065) - **Update** Terraform and AWS image updated to remove hardcoded SAML2AWS_SESSION_DURATION, which can leverage account defaults or customer overrides

## [2.6.21] - 2024-10-16
- [CNPT-1935](https://jira.express-scripts.com/browse/CNPT-1935) - **Update** Ansible image updated to ensure credential, if passed exists in AAP

## [2.6.20] - 2024-10-14
- [CNPT-2040](https://jira.express-scripts.com/browse/CNPT-2040) - **Add** module build description in base pipelines

## [2.6.19] - 2024-10-11
- **Remove** relocated code from cignaBuildFlow (no functional impact)

## [2.6.18] - 2024-10-01
- [CNPT-2018](https://jira.express-scripts.com/browse/CNPT-2018) - **Change** branchPattern usage to be consistent across use cases

## [2.6.17] - 2024-09-30
- [CNPT-1999](https://jira.express-scripts.com/browse/CNPT-1999) - **Fix** EPF extraCredentials and extraConfigs don't work on parallel phases

## [2.6.16] - 2024-09-30
- [CNPT-2036](https://jira.express-scripts.com/browse/CNPT-2036) - **Fix** Remove unsupported characters from webex notification messages

## [2.6.15] - 2024-09-26
- [CNPT-1930](https://jira.express-scripts.com/browse/CNPT-1930) - **Fix** Include missing psc in CurlRequester for sonarqg linting

## [2.6.14] - 2024-09-24
- [CNPT-2033](https://jira.express-scripts.com/browse/CNPT-2033) - **Fix** EPF to suppress epf_pod_group when groupBy is unique

## [2.6.13] - 2024-09-23
- [FDOTD-1216](https://jira.express-scripts.com/browse/FDOTD-1216) - **Change** JNLP upgrade to version 2.462.1.3

## [2.6.12] - 2024-09-20
- **Bug Fix** - Update compliance validator to set GitHub instance

## [2.6.11] - 2024-09-19
- **Bug Fix** - update python build commands to support setuptools deprecation of the ``upload`` command

## [2.6.10] - 2024-09-11
- [CNPT-1978](https://jira.express-scripts.com/browse/CNPT-1978) - **Change** compliance validator checkmarx project name logic to handle profile names

## [2.6.9] - 2024-09-13
#### No functional changes for consumers
- [CNPT-2011](https://jira.express-scripts.com/browse/CNPT-2011) - **Update** vendored version of jenkins-spock to 2.1.5

## [2.6.8] - 2024-09-12
- [CNPT-1698](https://jira.express-scripts.com/browse/CNPT-1698) - **Change** Replace . with _ in env var names, primarily for jenkins auto generated env vars, which enables phases to access these variables

## [2.6.7] - 2024-09-12
- [CNPT-2022](https://jira.express-scripts.com/browse/CNPT-2022) - MavenBuild does not quote deployerID or apiKey.
- [CNPT-2023](https://jira.express-scripts.com/browse/CNPT-2023) - Pipelines that are not multibranch pipelines fail to send emails.

## [2.6.6] - 2024-09-11
- [CNPT-2020](https://jira.express-scripts.com/browse/CNPT-2020) - Normalize the epf_pod_group so that it conforms to RFC 1123 which is required to name the JenkinsPod.

## [2.6.5] - 2024-09-11
- **Cleanup** - remove code that was commented out during QoL changes but not cleaned up before merge.
- **QoL** - Allow config.email to define a branchPattern; only send emails if the branch matches the branchPattern

## [2.6.4] - 2024-09-10
- [CNPT-1846](https://jira.express-scripts.com/browse/CNPT-1846) - **Enhance** Add subCommand & env for modules to the execution plan

## [2.6.3] - 2024-09-05
- QoL - **Add** support ability to zip a folder and attach it to an email.

## [2.6.2] - 2024-09-05
#### No functional changes for consumers
- [CNPT-2000](https://jira.express-scripts.com/browse/CNPT-2000) - **Add** vendored version of jenkins-spock to facilitate Java 11 move
- [CNPT-2000](https://jira.express-scripts.com/browse/CNPT-2000) - **Change** EPF's own build to use Java 11 for internal build/test

## [2.6.1] - 2024-09-03
- [CNPT-2004](https://jira.express-scripts.com/browse/CNPT-2004) - **Fix** Incorrectly formatted epf_pod_group labels.

## [2.6.0] - 2024-09-03
- [CNPT-1927](https://jira.express-scripts.com/browse/CNPT-1927) - **Enhance** Add the ability for epf to automatically tune pod/container resources using a simple max over time algorithm.

## [2.5.14] - 2024-09-03
- SCTASK8216544 - **Fix** regression issue with saml2aws config

## [2.5.13] - 2024-09-03
- SCTASK8216544 - **Add** ability to run an arbitrary script on a phase's 'primary' container before that phase executes

## [2.5.12] - 2024-08-30
- [CNPT-1998]([https://jira.express-scripts.com/browse/CNPT-1998]) - **Fix** Refactor aws federation to use single federate() method for all variants&phases

## [2.5.11] - 2024-08-29
- [BTT-4303]([https://jira.express-scripts.com/browse/BTT-4303]) - **Add** 'Playwright' framework type in QEAPythonRoboTest

## [2.5.10] - 2024-08-29
- Updated version of cnp-docker-k8s image with Helm chart changes including addition of PodDisruptionBudget and previewReplicaCount.

## [2.5.9] - 2024-08-28
- [CNPT-2001](https://jira.express-scripts.com/browse/CNPT-2001) **Change** Update core in pcf image to reflect cnp-tools which uses openshift snow-services api instead of PCF

## [2.5.8] - 2024-08-28
- **Refactor** to improve serialization and put some verbose logging behind the 'verbose' feature flag

## [2.5.7] - 2024-08-21
- **Change** rolling tag name away from main (for now) to avoid dev headaches

## [2.5.6] - 2024-08-21
- [CNPT-1845](https://jira.express-scripts.com/browse/CNPT-1845) - EPF's own build - **Add** check for version tag existence in PR build
- EPF's own build - **Change** script that extracts release notes from changelog to eliminate the need for explicit 'latest' markers

## [2.5.5] - 2024-08-20

### Framework
- [CNPT-1985](https://jira.express-scripts.com/browse/CNPT-1985) - **Remove** ephemeral-storage defaults

## [2.5.4] - 2024-08-19
### Framework
- [CNPT-1695](https://jira.express-scripts.com/browse/CNPT-1695) - **Change** EPF's internal tagging script to use 'main' as the rolling tag

## [2.5.3] - 2024-08-19
### Phases
- **Updated** CheckmarxScanning.groovy to expose Checkmarx's `-projectAS` parameter to end users.

## [2.5.2] - 2024-08-19

### Framework

### Helpers

### Phases
- [CNPT-1715](https://jira.express-scripts.com/browse/CNPT-1715) - **Changed** cnp pcf, k8s, core default image to new version to update tools which uses openshift snow-services api instead of PCF

## [2.5.1] - 2024-08-14

### Framework

### Helpers

### Phases
- [CNPT-1932](https://jira.express-scripts.com/browse/CNPT-1932) **Bug Fix** Update pod provisioning logic to ensure both curl container & eks configuration are correct if running in EKS & need nested openshift pod

## [2.5.0] - 2024-08-14

### Framework

### Helpers

### Phases
- **Change** Support for buildContextPath, secretFiles configuration directives in Podman packaging phase

## [2.4.0] - 2024-08-14

### Framework

### Helpers

### Phases
- **Remove** Plz packaging phase as docker in docker is no longer supported on CloudBees Jenkins

## [2.3.2] - 2024-08-13

### Framework

### Helpers

### Phases
- [CNPT-1757](https://jira.express-scripts.com/browse/CNPT-1757) **Added** Enabled kaniko packaging phases to override saml2aws container

## [2.3.1] - 2024-08-12

### Framework

### Helpers
- **Change** default resources for JNLP containers including from Compliance Validator

### Phases

## [2.3.0] - 2024-08-09

### Framework

### Helpers

### Phases
- **Remove** MultiDocker packaging phase as docker in docker is no longer supported on CloudBees Jenkins

## [2.2.1] - 2024-08-09

### Framework

### Helpers

### Phases
- **Add** publishing, wrapper configuration, build/publish task configuration to gradle build phase
- **Change** gradle linting configuration to support the same options for gradle user home and gradle wrapper scripts as gradle build

## [2.2.0] - 2024-08-06

### Framework

### Helpers
- **Remove** Cobol linting, build, packaging, and deployment phases due to no use

### Phases

## [2.1.1] - 2024-08-06

### Framework

### Helpers
- **Change** how library name/branch are determined when prepping for callback jobs

### Phases

## [2.1.0] - 2024-08-05

### Framework

### Helpers

### Phases
- **Remove** IIB build phase due to no use and ACE build phase replacement

## [2.0.16] - 2024-08-02

### Framework

### Helpers

### Phases
- **Add** stage view items for Sonar/Checkmarx that run as part of a build phase

## [2.0.15] - 2024-08-02

### Framework
- [CNPT-1877](https://jira.express-scripts.com/browse/CNPT-1877) - **Updated** when using modules, sonar scanner is upgraded to version 6 in the java images

### Helpers

### Phases

## [2.0.14] - 2024-07-30

### Framework

### Helpers

### Phases
- SCTASK8016141 - **Fix** child phases of Parallel phases don't get initialized
- **Add** Parallel phase honoring 'stageName' configuration value

## [2.0.13] - 2024-07-26

### Framework
- [CNPT-1883](https://jira.express-scripts.com/browse/CNPT-1883) - **Added** 'metadataInArgs' flag to control if metadata should be included in module arguments

### Helpers

### Phases

## [2.0.12] - 2024-07-25

### Framework

### Helpers

### Phases
- [CNPT-1907](https://jira.express-scripts.com/browse/CNPT-1907) **Removed** default env var ANSIBLE_TOKEN as EPF is designed to default the cred id to env var when defined in the contract

## [2.0.11] - 2024-07-25

### Framework
- [CNPT-1764](https://jira.express-scripts.com/browse/CNPT-1764) - **Add** ability to send emails upon successful completion of most phase types.

### Helpers

### Phases

## [2.0.10] - 2024-07-24

### Framework

### Helpers

### Phases
- **Fix** dotnetcore build phase artifactory publish endpoint to remove duplicative artifactory directory

## [2.0.9] - 2024-07-24

### Framework

### Helpers

### Phases
- **Refactor** maven-specific build and publish conditions into maven build phase
- **Refactor** publish conditional to make it more flexible for specific build types

## [2.0.8] - 2024-07-24

### Framework

### Helpers

### Phases
- [CNPT-1716](https://jira.express-scripts.com/browse/CNPT-1716) - **Changed** cnp pcf, k8s, core default image to new version to update tools which uses snow-services api instead of cmdb api

## [2.0.7] - 2024-07-18

### Framework

### Helpers

### Phases
- [CNPT-1891](https://jira.express-scripts.com/browse/CNPT-1891) - **Fix** Remote Type Jenkins UI Panel and error handling

## [2.0.6] - 2024-07-17

### Framework

### Helpers

### Phases
- [CNPT-1911](https://jira.express-scripts.com/browse/CNPT-1911) - **Fix** Ensure PSC is propagated to deployment normal-changes

## [2.0.5] - 2024-07-17

### Framework

### Helpers

### Phases
- [BTT-4180](https://jira.express-scripts.com/browse/BTT-4180) - **Add** continueOnTestFailure flag to QEAPythonRoboTest to allow for failing a pipeline based on failed tests

## [2.0.4] - 2024-07-15

### Framework
- **Fix** a case where setting up Compliance Validator state could go into an infinite recursion

### Helpers

### Phases

## [2.0.3] - 2024-07-12

### Framework
- **Fix** init method not getting called on subphases of phases deployment
- **Fix** pod group assignment on nested phases when checkpoint(s) present

### Helpers

### Phases

## [2.0.2] - 2024-07-12

### Framework

### Helpers

### Phases
- [FDOTD-1165](https://jira.express-scripts.com/browse/FDOTD-1165) **Changed** default jnlp image to tag cloudbees-2.452.2.3

## [2.0.1] - 2024-07-11

### Framework
- SCTASK7976658 - **Fix** a missing parameter that causes a null reference if `envInMetadata` is used

### Helpers

### Phases

## [2.0.0] - 2024-07-09

### Framework
- [CNPT-1884](https://jira.express-scripts.com/browse/CNPT-1884) **Update** Refactor epf to remove all global state. split pods along checkpoint boundaries

### Helpers

### Phases

## [1.0.25] - 2024-07-02

### Framework
- [CNPT-1895](https://jira.express-scripts.com/browse/CNPT-1882) **Update** Ansible docker image to include inventoryName parameter

### Helpers

### Phases

## [1.0.24] - 2024-06-24

### Framework

### Helpers
- **Update** Compliance Validator message to avoid stack overflow from infinite recursion

### Phases

## [1.0.23] - 2024-06-20

### Framework

### Helpers

### Phases
- [CNPT-1859](https://jira.express-scripts.com/browse/CNPT-1859) - **Update** 'phases' deployment phase to use `extraCredentials` values
from outer scopes and each sub-phase

## [1.0.22] - 2024-06-17

### Framework

### Helpers
- [CNPT-1507](https://jira.express-scripts.com/browse/CNPT-1507) **Added** Dynatrace as an alternative to Grafana for resource dashboards - toggled by feature flag

### Phases

## [1.0.21] - 2024-06-17

### Framework

### Helpers

### Phases
- [BTT-4095](https://jira.express-scripts.com/browse/BTT-4095):
  - QEAPythonRoboTest:
    - **Update** phase to handle Mainframe/Web Templates
    - **Add** Zephyr functionality
    - **Update** behavior when invoking tests fails to cause phase to not fail

## [1.0.20] - 2024-06-14

### Framework
- [CNPT-1642](https://jira.express-scripts.com/browse/CNPT-1642) - **Update** BuildConfiguration to ignore extra properties when casting. This allows release packages to be read by callback jobs, even if the structure of the class has changed since the release package was created.

### Helpers

### Phases

## [1.0.19] - 2024-06-13

### Framework

### Helpers

### Phases
- [FDSSTTD-1364](https://jira.express-scripts.com/browse/FDSSTTD-1364) - **Added** template.scm_branch parameter to Ansible Tower deployment phase to specify optional scm branch override in branch of template

## [1.0.18] - 2024-06-13

### Framework

### Helpers

### Phases
-  **\[Updated]** \[CheckmarxScanning.groovy] to expose Checkmarx's `-ForceScan` parameter to end users.

## [1.0.17] - 2024-06-06

### Framework
- [CNPT-1845](https://jira.express-scripts.com/browse/CNPT-1845) - **Update** build process to fix credentialing issue (in EPF's own build; not a consumer-facing concern)

### Helpers

### Phases

## [1.0.16] - 2024-06-06

### Framework

### Helpers

### Phases
- [CNPT-1858](https://jira.express-scripts.com/browse/CNPT-1858) **fixed** jfrog command updates as per latest jfrog image in ace and iib builds

## [1.0.15] - 2024-06-04

### Framework
- [CNPT-1845](https://jira.express-scripts.com/browse/CNPT-1845) - **Update** build process to dedupe some maven runs (in EPF's own build; not a consumer-facing concern)

### Helpers

### Phases

## [1.0.14] - 2024-05-29

### Framework

### Helpers

### Phases
- [CNPT-1851](https://jira.express-scripts.com/browse/CNPT-1851)
  * [Updated] Build & ScanOnly to detect which build types should not be scanned via sonarqube or checkmarx
  * [Added] detection of misconfigured sonarqube scan

## [1.0.13] - 2024-05-29

### Framework

### Helpers

### Phases
- [CNPT-1820](https://jira.express-scripts.com/browse/CNPT-1820) **fixed** resources in scanonly and build phase for sonar and checkmarx

## [1.0.12] - 2024-05-29

### Framework
- [CNPT-1849](https://jira.express-scripts.com/browse/CNPT-1849) **Changed** Update Git Commit Status message description

### Helpers

### Phases

## [1.0.11] - 2024-05-23

### Framework

### Helpers

### Phases
- [CNPT-1583](https://jira.express-scripts.com/browse/CNPT-1583) **Changed** cnp-node default image to new version to fix auth issues on publishing to artifactory

## [1.0.10] - 2024-05-22

### Framework

### Helpers

### Phases
- [CNPT-1733](https://jira.express-scripts.com/browse/CNPT-1733) **Changed** default node image to tag lts-alpine (node 20) and add default publish scope to @cigna as node 20 requires scope

## [1.0.9] - 2024-05-15

### Framework

### Helpers

### Phases
- [CNPT-1736](https://jira.express-scripts.com/browse/CNPT-1736) **Fixed** jmeter CVE update tag to 5.6.3

## [1.0.8] - 2024-05-13

### Framework
- [CNPT-1798](https://jira.express-scripts.com/browse/CNPT-1798) **Fixed** Pod checkout issues and artifact persistence

### Helpers

### Phases

## [1.0.7] - 2024-05-07

### Framework

### Helpers

### Phases
- [CNPT-1796](https://jira.express-scripts.com/browse/CNPT-1796) **Changed** QEAMavenTest & QEAMavenTestSpec to support ability to enable or disable the use of the orchestrator provided (and sanctioned) settings.xml. Due to backwards compatibility with existing phase consumers, it defaults to false which will fall back on utilizing the settings.xml baked in to the container.

## [1.0.6] - 2024-05-07

### Framework

### Helpers
-  **\[Added]** `getDefaultBranchName()` to CommonGit for fetching default GitHub branch

### Phases

-  **\[Changed]** \[Phase.groovy] to introduce new required parameter for Checkmarx scanning
-  **\[Changed]** \[Build.groovy] to increment the Checkmarx-Toolshack image version
-  **\[Changed]** \[CheckmarxScanning.groovy] to take advantage of the dso-cli on the new toolshack version
-  **\[Changed]** default java image to cnp-docker-maven-java8:1.1.4

## [1.0.5] - 2024-05-06

### Framework

### Helpers
- **Changed** default sonar image to tag 4.8.1

### Phases

## [1.0.4] - 05-05-2024

### Framework
- [CNPT-1792](https://jira.express-scripts.com/browse/CNPT-1792) **Changed** set FeatureFlags.scm.legacyCheckout and suppressWorkspaceSafeDirectory flags correctly before they are validated to execute the intended functionality

### Helpers

### Phases

## [1.0.3] - 04-29-2024

### Framework

### Helpers

### Phases
- [CNPT-1770](https://jira.express-scripts.com/browse/CNPT-1770) **Changed** default k8s (module) image from 1.0.7-dev-ov2 to 1.1.1

## [1.0.2] - 04-26-2024

### Framework

### Helpers

### Phases
- Fixed bug in AnsibleTowerDeployment that avoids an error of `Deployment failed before application could be deployed: Cannot invoke method split() on null object` when template.credentialsId is null.

## [1.0.1] - 04-25-2024

### Framework
- [CNPT-1650](https://jira.express-scripts.com/browse/CNPT-1650) **Changed** Tag-and-Release.sh
  Anytime an EPF PR is merged, the repo will be tagged with

1. the version in pom.xml
2. release

Post merge a release with name derived the version in pom.xml will be created
This will enable jobs which are not tied to a specific tag or branch to be linked to the latest release, in other words, when just epf is referred, when callback in involved

### Helpers

### Phases

## [main]

- [CNPT-1562](https://jira.express-scripts.com/browse/CNPT-1562) **\[Updated]** MCR to point to correct AWS memberweb module and subcommand
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Changed]** \[Build.groovy] to introduce a logical name for Checkmarx & Sonar containers
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Changed]** \[CobolBuild.groovy] to use NameRegistry in order to resolve true container name from logical name
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Changed]** \[DotnetCoreBuild.groovy] to use NameRegistry in order to resolve true container name from logical name
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Added]** \[NameRegistry.groovy] to provide the ability to map logical container names to their actual versioned container names
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Changed]** \[ReltioDeployment.groovy] to use correct containerName for main container
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Changed]** \[UCDDeployment.groovy] to use NameRegistry in order to resolve true container name from logical name
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Changed]** \[SonarScanning.groovy] to use NameRegistry in order to resolve true container name from logical name
- [CNPT-1221](https://jira.express-scripts.com/browse/CNPT-1221) **\[Changed]** \[cignaBuildFlow.groovy] to use report logical to actual name mappings if verbose featureFlag is set (to aid in debugging)
- [CNPT-1326](https://jira.express-scripts.com/browse/CNPT-1326) **\[Changed]** \[UCDDeployment.groovy] to use correct container name
- [CNPT-1348](https://jira.express-scripts.com/browse/CNPT-1348) **\[Changed]** \[GenericOSCPDeployment.groovy] to ensure outermost nested containers are accessible from the innermost container
- [CNPT-1331](https://jira.express-scripts.com/browse/CNPT-1331) **\[Changed]** \[KanikoPackaging.groovy] to fix the scanning and tagging strategies
- [CNPT-1331](https://jira.express-scripts.com/browse/CNPT-1331) **\[Changed]** \[PodmanPackaging.groovy] to fix the scanning and tagging strategies
- [CNPT-1339](https://jira.express-scripts.com/browse/CNPT-1339) **\[Changed]** \[CommonGit.groovy] to fix the HS github instances failing during sha checks
- [CNPT-1342](https://jira.express-scripts.com/browse/CNPT-1342) **\[Changed]** \[NodeBuild.groovy] to fix issue with NPM&YARN phases not respecting the package publish path.
- [CNPT-1347](https://jira.express-scripts.com/browse/CNPT-1347) **\[Changed]** \[Deployment.groovy] to fix issue with implicit ticket phases not having access to the curl container.

---
<!-- Footnote links -->

[1]: https://keepachangelog.com/en/1.0.0/
[2]: https://semver.org/spec/v2.0.0.html
