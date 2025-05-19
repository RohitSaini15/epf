package com.evernorth.cloudnativebuild.service

import com.cigna.common.utils.FeatureFlags
import com.cigna.modules.ModuleBridgeException
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.ModuleContractType
import hudson.Functions

class ModuleUtil {
    static Map<String, ModuleContractType> contractTypeMap = [
            (PipelineConstants.VERB_BUILD)     : ModuleContractType.BUILD,
            'render'                           : ModuleContractType.BUILD,
            "buildimage"                       : ModuleContractType.CONTAINER,
            "createpackages"                   : ModuleContractType.PACKAGE,
            "cutover"                          : ModuleContractType.CUTOVER,
            (PipelineConstants.VERB_DEPLOY)    : ModuleContractType.DEPLOY,
            "apply"                            : ModuleContractType.DEPLOY,
            "event"                            : ModuleContractType.EVENT,
            "notify"                           : ModuleContractType.NOTIFY,
            "preflightcheckverification"       : ModuleContractType.PREFLIGHT_CHECK,
            "provision"                        : ModuleContractType.PROVISION,
            "publishpackages"                  : ModuleContractType.PUBLISH,
            "publishimage"                     : ModuleContractType.PUBLISH_IMAGE,
            (PipelineConstants.VERB_RELEASE)   : ModuleContractType.RELEASE,
            "runqualitycheck"                  : ModuleContractType.QUALITY_CHECK,
            "runsecurityscan"                  : ModuleContractType.SECURITY_SCAN,
            "runtest"                          : ModuleContractType.TEST,
            "checkoutfromscm"                  : ModuleContractType.SCM_CHECKOUT,
            "finalizerelease"                  : ModuleContractType.FINALIZE_RELEASE,
            "loadconfiguration"                : ModuleContractType.CONFIG,
            (PipelineConstants.VERB_PRERELEASE): ModuleContractType.PRERELEASE,
            (PipelineConstants.VERB_RUN_SCRIPT): ModuleContractType.SCRIPT,
            (PipelineConstants.VERB_AWAIT)     : ModuleContractType.APPROVAL,
            "other"                            : ModuleContractType.OTHER
    ]

    @NonCPS
    static ModuleContractType getContractTypeForVerb(String verb) {
        return contractTypeMap[verb.toLowerCase()]
    }

    @NonCPS
    static boolean isNotShellModule(String verb){
        def internalModules = [
                PipelineConstants.VERB_RELEASE,
                PipelineConstants.VERB_PRERELEASE,
                PipelineConstants.VERB_RUN_SCRIPT,
                PipelineConstants.VERB_AWAIT]
        return internalModules.contains(verb.toLowerCase())
    }

    @NonCPS
    static String getMasterName(def script) {
        String masterName
        try {
            masterName = script.env.JENKINS_URL.tokenize('/').last()
        } catch (e) {
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(e))
            }
            throw new ModuleBridgeException("Unable to determine master name: ${e.message}")
        }

        masterName
    }
}
