package com.evernorth.cloudnativebuild.model

import com.cloudbees.groovy.cps.NonCPS


class BuildInfo implements Serializable{
    String commitMessage
    String commitId
    String commitAuthorName
    String buildName
    String buildUrl
    String buildDisplayName
    String buildDescription
    String buildNumber
    String buildType
    String jobName
    String jobRepository
    String buildTag
    String jobUrl
    String branchName
    String cloudNativePipelineModuleConfiguration
    String xlTemplateName
    String buildTargetFile
    String buildGoals
    String buildTool
    String mavenOpts
    String nodeName
    String buildOutputPath  // The path to the folder where the compiled output will be stored.
    String enterpriseReleaseId
    String jenkinsfileDirectory = '.'
    String extraDeployFiles
    String workspace
    String artifactoryRootUrl
    String artifactoryCredential
    String npmCredentialsId    
    String artifactoryDockerUrl
    String artifactoryDockerRegistry
    String artifactoryReleaseRepo = 'libs-release-local'
    String artifactorySnapshotRepo = 'libs-snapshot-local'
    String artifactoryMavenReleaseResolverRepo = 'libs-release'
    String artifactoryMavenSnapshotResolverRepo = 'libs-snapshot'
    String artifactoryGradleResolverRepo = 'esi-maven-virtual'
    String artifactoryDeploymentReleaseRepo = 'ci-release-local'
    String artifactoryDeploymentSnapshotRepo = 'ci-snapshot-local'
    String artifactoryYumRepo = 'yum-local'
    String gitCredential
    String gitRootUrl
    String cnpCignaGit
    String cxEndpoint
    String jobBuildCred
    String cxServerAddress
    String sonarCredentialId
    String sonarHost
    String cxCredential
    String xlReleaseUrl
    String xlReleaseUrlAPI


    @Override
    @NonCPS
    String toString() {
        return """\
BuildParams{
	commitMessage='$commitMessage',
    commitId='$commitId',
	commitAuthorName='$commitAuthorName',
    cloudNativePipelineModuleConfiguration='$cloudNativePipelineModuleConfiguration',
    buildName='$buildName', 
    buildUrl='$buildUrl',
    buildDisplayName='$buildDisplayName', 
    buildDescription='$buildDescription', 
    buildNumber='$buildNumber', 
    jobName='$jobName', 
    jobRepository='$jobRepository', 
    buildTag='$buildTag', 
    jobUrl='$jobUrl', 
    branchName='$branchName', 
    xlTemplateName='$xlTemplateName', 
    buildTargetFile='$buildTargetFile', 
    buildGoals='$buildGoals', 
    buildTool='$buildTool',
    buildType='$buildType', 
    mavenOpts='$mavenOpts',
    nodeName='$nodeName', 
    buildOutputPath='$buildOutputPath', 
    enterpriseReleaseId='$enterpriseReleaseId', 
    workspace='$workspace', 
    artifactoryRootUrl='$artifactoryRootUrl', 
    artifactoryCredential='$artifactoryCredential', 
    artifactoryDockerUrl='$artifactoryDockerUrl', 
    artifactoryDockerRegistry='$artifactoryDockerRegistry', 
    artifactoryReleaseRepo='$artifactoryReleaseRepo', 
    artifactorySnapshotRepo='$artifactorySnapshotRepo',
    artifactoryMavenSnapshotResolverRepo='$artifactoryMavenSnapshotResolverRepo',
    artifactoryMavenReleaseResolverRepo='$artifactoryMavenReleaseResolverRepo',
    artifactoryGradleResolverRepo='$artifactoryGradleResolverRepo',
    artifactoryDeploymentReleaseRepo='$artifactoryDeploymentReleaseRepo', 
    artifactoryDeploymentSnapshotRepo='$artifactoryDeploymentSnapshotRepo', 
    artifactoryYumRepo='$artifactoryYumRepo', 
    gitCredential='$gitCredential', 
    gitRootUrl='$gitRootUrl',
    jobBuildCred='$jobBuildCred',
    cnpCignaGit='$cnpCignaGit',
    cxEndpoint='$cxEndpoint',
    cxServerAddress='$cxServerAddress',
    sonarCredentialId='$sonarCredentialId',
    sonarHost='$sonarHost',
    cxCredential='$cxCredential',
    xlReleaseUrl='$xlReleaseUrl', 
    xlReleaseUrlAPI='$xlReleaseUrlAPI', 
    npmCredentialsId='$npmCredentialsId', 
    jenkinsfileDirectory=$jenkinsfileDirectory,
    extraDeployFiles=$extraDeployFiles
}"""
    }
}
