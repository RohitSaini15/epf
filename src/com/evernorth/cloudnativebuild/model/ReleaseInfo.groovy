package com.evernorth.cloudnativebuild.model

import com.cloudbees.groovy.cps.NonCPS

class ReleaseInfo implements Serializable{
    List<ModuleContract> moduleContractList
    Date executionDateTime
    BuildConfiguration buildConfiguration
    BuildInfo buildInfo
    Map releaseArguments
    String releaseId
    def artifact
    def rollbackPackagePath
    List<StepInvocation> steps
    String deploymentPackagePath


    @Override
    @NonCPS
    String toString() {
        return "ReleaseInfo{" +
                "moduleContractList=" + moduleContractList +
                ", executionDateTime=" + executionDateTime +
                ", buildConfiguration=" + buildConfiguration +
                ", buildInfo=" + buildInfo +
                ", releaseArguments=" + releaseArguments +
                ", releaseId='" + releaseId + '\'' +
                ", artifact=" + artifact +
                ", rollbackPackagePath=" + rollbackPackagePath +
                ", steps=" + steps +
                ", deploymentPackagePath='" + deploymentPackagePath + '\'' +
                '}';
    }
}
