# Jenkins to GitHub Actions Migration Guide

This document outlines the migration of the Enterprise Pipeline Framework (EPF) from Jenkins to GitHub Actions.

## Overview

The Enterprise Pipeline Framework was originally designed as a Jenkins shared library that provided a structured approach to CI/CD pipelines. This migration converts the Jenkins pipeline into equivalent GitHub Actions workflows.

## Key Components Migrated

### 1. Pipeline Phases

Jenkins EPF phases have been converted to GitHub Actions jobs:

| Jenkins Phase | GitHub Actions Job |
|---------------|-------------------|
| Compile code | `compile` |
| Precheck POM version | `precheck-pom-version` |
| Tests | `tests` |
| Quality Scans | `quality-scans` |
| Tag Release | `tag-and-release` |

### 2. Environment Variables

Jenkins environment variables have been mapped to GitHub Actions environment variables:

```yaml
env:
  CNP_LOG_LEVEL: TRACE
  CNP_OVERRIDE_COMMON: 'true'
  CX_CREDENTIAL: ${{ secrets.CHECKMARX_CREDENTIALS }}
  SONAR_CREDENTIAL_ID: ${{ secrets.SONAR_TOKEN }}
  CNP_DEFAULT_JAVA_IMAGE: 'openjdk:11'
  ARTIFACTORY_ROOT_URL: https://cigna.jfrog.io/artifactory
```

### 3. Branch Patterns

The Jenkins pipeline used branch patterns to determine which phases to run. In GitHub Actions, this is handled through the workflow trigger configuration and conditional job execution:

```yaml
on:
  push:
    branches: [ main, release/*, hotfix/* ]
  pull_request:
    branches: [ main ]
```

### 4. Credentials and Secrets

Jenkins credentials have been migrated to GitHub Secrets:

- `CHECKMARX_CREDENTIALS` → GitHub Secret
- `SONAR_TOKEN` → GitHub Secret
- `GITHUB_TOKEN` → Built-in GitHub Secret

## Additional Migration Steps Required

1. **Set up GitHub Secrets**:
   - Add `SONAR_TOKEN` and `SONAR_HOST_URL` secrets
   - Add Checkmarx credentials (`CHECKMARX_TENANT`, `CHECKMARX_CLIENT_ID`, `CHECKMARX_CLIENT_SECRET`)

2. **Update Scripts**:
   - The `Tag-and-Release-CI.sh` script is reused but may need adjustments for GitHub Actions

3. **Configure Branch Protection Rules**:
   - Set up branch protection rules for the `main` branch to require status checks to pass

4. **Advanced Features to Implement**:
   - Implement matrix builds for testing across multiple environments
   - Set up deployment workflows for different environments
   - Configure caching for dependencies to improve build times

## Differences Between Jenkins and GitHub Actions

1. **Execution Environment**:
   - Jenkins: Custom containers with specific tools
   - GitHub Actions: GitHub-hosted runners with pre-installed tools

2. **Pipeline Definition**:
   - Jenkins: Groovy-based DSL
   - GitHub Actions: YAML-based workflow files

3. **Shared Libraries**:
   - Jenkins: Groovy-based shared libraries
   - GitHub Actions: Reusable workflows and composite actions

## Next Steps

1. Test the GitHub Actions workflow thoroughly
2. Gradually phase out the Jenkins pipeline
3. Implement additional GitHub Actions features as needed
4. Update documentation and training materials