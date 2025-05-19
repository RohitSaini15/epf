package com.cigna.common.utils

import com.cloudbees.groovy.cps.NonCPS

class FeatureFlags {
    /*
      user-settable feature flags
    */
    static boolean showStackTraces = false
    // in ComplianceValidator, call adjudicator regardless of whether pipeline contains prod deploy
    static boolean nonProdComplianceChecks = true
    static boolean verbose = true
    static boolean debug = false
    static boolean reportOnNamespace = true
    // in jobs using the CNP engine (callback), use script.checkout instead of CommonGit.checkout
    static def scm = [
        legacyCheckout: false
    ]
    // create a checkpoint after each pod group
    static boolean injectCheckpoints = false

    /**
     * Holds the feature flag settings for pod auto-tuning
     *
     * 'enabled' indicates if the autotuning feature is enabled; When 'enabled' is true, epf will
     * attempt to retrieve JenkinsPod custom resources from the namespace for each pod group.
     *
     * 'dryRun' specifies whether the autotuning should be executed in dry run mode;
     * If 'dryRun' is true and a CR was fetched for the currently active pod group,
     * epf will print the recommendations to the console log, but will *not* act upon
     * those recommendations. If dryRun is false while enabled is true, then, if epf fetches recommendations for the
     * currently active pod group, it will apply those recommendations to any containers that have recommendations.
     *
     * This will override any per-phase container configurations that are hard coded in the Jenkinsfile.
     */
    static def podAutotuning = [
        enabled: false,
        dryRun : true
    ]

    /*
      internal pipeline administrator feature flags - not settable by users
    */
    // monitoring platform to which messaging should direct users
    static String monitoringPlatform = 'grafana'
    // if true, StashUtils will skip stashing/unstashing if psc.podSelector has only one pod
    // this is generally a desired optimization when all the pods are known at the start of the pipeline,
    // but in callbacks, pods might get "added" later, so we want to be able to stash, even if there's only one pod at the time
    static boolean skipStashingWhenSinglePod = true

    @NonCPS
    static void setFromMap(Map flagMap) {
        showStackTraces = flagMap.get('showStackTraces', false)
        nonProdComplianceChecks = flagMap.get('nonProdComplianceChecks', true)
        injectCheckpoints = flagMap.get('injectCheckpoints', false)
        debug = flagMap.get('debug', false)
        verbose = flagMap.get('verbose', false)
        def auto = flagMap.podAutotuning as Map ?: [:]
        podAutotuning.enabled = auto.get('enabled', podAutotuning.enabled)
        podAutotuning.dryRun = auto.get('dryRun', podAutotuning.dryRun)
        def mapScm = flagMap.scm as Map ?: [:]
        scm.legacyCheckout = mapScm.get('legacyCheckout', false)
        reportOnNamespace = flagMap.get('reportOnNamespace', true)
    }

    static String asString() {
        return "Feature Flags: showStackTraces=${showStackTraces}, " +
            "nonProdComplianceChecks=${nonProdComplianceChecks}, " +
            "debug=${debug}, verbose=${verbose}, " +
            "legacyCheckout=${scm.legacyCheckout}, " +
            "injectCheckpoints=${injectCheckpoints}, " +
            "podAutotuning=${podAutotuning}"
    }

}
