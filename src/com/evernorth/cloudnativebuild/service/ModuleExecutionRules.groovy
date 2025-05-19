package com.evernorth.cloudnativebuild.service

import com.cloudbees.groovy.cps.NonCPS

class ModuleExecutionRules {
    private static final List DEFAULT_DEPLOYABLE_BRANCHES = ["^develop\$", "^release.*", "^hotfix.*"]

    @NonCPS
    static boolean isDeployableBranch(String deployableBranches, String branch) {
        List deployableBranchesList = deployableBranches != null ? deployableBranches?.split(',')?.toList() : DEFAULT_DEPLOYABLE_BRANCHES
        boolean flag = false
        if (deployableBranchesList) {
            deployableBranchesList.each {
                if (branch ==~ it) {
                    flag = true
                }
            }
        }
        return flag
    }

    static boolean isReleasableBranch(def script, def config = [:], def currentBranch = script.env.BRANCH_NAME) {
        ( currentBranch ==~ /${findReleasePattern(config)}/ ) || config?.isProductionDeployment == true
    }

    static String findReleasePattern(config) {
        config?.releaseBranchPattern ?: config?.branchPattern ?: '^(release|hotfix).*'
    }
}
