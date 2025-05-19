# GitHub Actions Workflows for Enterprise Pipeline Framework

This directory contains GitHub Actions workflows that replace the Jenkins pipeline functionality of the Enterprise Pipeline Framework (EPF).

## Available Workflows

### 1. Main Workflow (`main.yml`)

The standard workflow that runs on push to main branch and pull requests. It includes:

- Code compilation
- POM version precheck for PRs
- Running tests
- Quality scans (SonarQube and Checkmarx)
- Tagging and releasing (for main branch)

### 2. Advanced Workflow (`advanced.yml`)

An enhanced workflow with additional features:

- Matrix builds across multiple Java versions
- Separate unit and integration tests
- Manual workflow dispatch with environment selection
- Deployment to different environments
- ServiceNow change request creation for production deployments
- Notifications

### 3. Reusable Workflow (`reusable.yml`)

A reusable workflow that can be called from other repositories:

```yaml
jobs:
  call-epf-workflow:
    uses: cigna/enterprise-pipeline-framework/.github/workflows/reusable.yml@main
    with:
      java-version: '11'
      maven-goals: 'verify'
      sonar-project-key: 'my-project'
      checkmarx-project-name: 'my-project'
      deploy-environment: 'dev'
      run-release: false
    secrets:
      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
      CHECKMARX_TENANT: ${{ secrets.CHECKMARX_TENANT }}
      CHECKMARX_CLIENT_ID: ${{ secrets.CHECKMARX_CLIENT_ID }}
      CHECKMARX_CLIENT_SECRET: ${{ secrets.CHECKMARX_CLIENT_SECRET }}
```

## Required Secrets

To use these workflows, you need to set up the following secrets in your GitHub repository:

- `SONAR_TOKEN`: Token for SonarQube authentication
- `SONAR_HOST_URL`: URL of your SonarQube instance
- `CHECKMARX_TENANT`: Checkmarx tenant name
- `CHECKMARX_CLIENT_ID`: Checkmarx client ID
- `CHECKMARX_CLIENT_SECRET`: Checkmarx client secret
- `ARTIFACTORY_USERNAME`: Username for Artifactory (for deployments)
- `ARTIFACTORY_PASSWORD`: Password for Artifactory (for deployments)
- `SERVICENOW_TOKEN`: Token for ServiceNow API (for change requests)
- `WEBEX_TOKEN`: Token for WebEx notifications

## Customizing the Workflows

### Branch Protection Rules

It's recommended to set up branch protection rules for the `main` branch:

1. Go to Settings > Branches
2. Add a rule for the `main` branch
3. Require status checks to pass before merging
4. Require pull request reviews before merging

### Environment Configuration

For deployment workflows, set up environments in your repository:

1. Go to Settings > Environments
2. Create environments for `dev`, `test`, and `prod`
3. Add environment-specific secrets
4. Configure required reviewers for production deployments

## Migration Notes

For detailed information about the migration from Jenkins to GitHub Actions, see the [Jenkins to GitHub Actions Migration Guide](JENKINS_TO_GITHUB_ACTIONS_MIGRATION.md).