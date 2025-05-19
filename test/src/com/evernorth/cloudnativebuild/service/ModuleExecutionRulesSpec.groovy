package com.evernorth.cloudnativebuild.service

import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.service.ModuleExecutionRules
import spock.lang.Specification

class ModuleExecutionRulesSpec extends Specification{
    def "test isDeployableBranch with develop"(){
        when:
        boolean deployableBranch = ModuleExecutionRules.isDeployableBranch(PipelineConstants.DEFAULT_DEPLOYABLE_BRANCHES,"develop")
        then:
        deployableBranch
    }

    def "test isDeployableBranch with hotfix without env var setup"(){
        when:
        boolean deployableBranch = ModuleExecutionRules.isDeployableBranch(PipelineConstants.DEFAULT_DEPLOYABLE_BRANCHES,"hotfix/v1.0.0")
        then:
        deployableBranch
    }

    def "test isDeployableBranch with hotfix"(){
        when:
        boolean deployableBranch = ModuleExecutionRules.isDeployableBranch(PipelineConstants.DEFAULT_DEPLOYABLE_BRANCHES,"hotfix/v1.0.0")
        then:
        deployableBranch
    }

    def "test isDeployableBranch with release"(){
        when:
        boolean deployableBranch = ModuleExecutionRules.isDeployableBranch(PipelineConstants.DEFAULT_DEPLOYABLE_BRANCHES,"release/v1.0.0")
        then:
        deployableBranch
    }
    def "test isDeployableBranch with feature"(){
        when:
        boolean deployableBranch = ModuleExecutionRules.isDeployableBranch(PipelineConstants.DEFAULT_DEPLOYABLE_BRANCHES,"feature/somefeature")
        then:
        !deployableBranch
    }
}
