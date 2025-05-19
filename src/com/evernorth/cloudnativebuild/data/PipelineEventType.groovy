package com.evernorth.cloudnativebuild.data

/**
 * This class defines a set of Strings that describe pipeline stages
 */
enum PipelineEventType {
    /**
     * preCheckOut - This event fires at the start of the pipeline script, before SCM.
     */
    pipelineInitialize('PIPELINE_INITIALIZE'),

    /**
     * scmCheckout - This stage is to pull the source code from a repository. This is usually accomplished by the
     * Jenkins checkout command but can be overwritten by a custom module
     */
    scmCheckoutStarted("SCM_CHECKOUT"),

    /**
     * scmCheckoutCompleted - This phase occurs after checkout but before stash and runs in the same container as the checkout.
     * All modules that use this phase must use the same container as the checkout
     */
    scmCheckoutCompleted("PRE_STASH"),

    /**
     * stashStarted - This event fires when a stash Operation completes saving workspace
     * file to a persistent disk
     */
    stashCompleted("STASH_COMPLETED"),

    /**
     * buildStarted - This event occurs after the files pulled from the scm have been stashed. The pipeline will call
     * unstash prior to running this phase since this block could be on another node
     */
    buildStarted("PRE_BUILD"),

    /**
     * buildCompleted - This event occurs after the files pulled from the scm have been stashed. The pipeline will call
     * unstash prior to running this phase since this block could be on another node
     */
    buildCompleted("BUILD"),

    /**
     * createPackageStarted - This event is triggered when a packing operation begins
     */
    createPackageStarting("PACKAGE_STARTING"),

    /**
     * createPackageCompleted - This event is triggered when a packing operation completes
     */
    createPackageCompleted("PACKAGE_COMPLETED"),

    /**
     * packagePublishStarted - This event is triggered when a package publish module starts
     */
    packagePublishStarting("PACKAGE_PUBLISH_STARTING"),

    /**
     * packagePublishStarted - This event is triggered when a package publish module starts
     */
    packagePublishCompleted("PACKAGE_PUBLISH_COMPLETED"),

    /**
     * provisionStarted - This event is triggered when a provisioning process For example;
     * start a terraform module to create a new node on a AKS Kubernetes Cluster
     */
    provisionStarting("PROVISION_STARTING"),

    /**
     * provisionCompleted - This event is triggered when a provisioning task completed
     */
    provisionCompleted("PROVISION_COMPLETED"),

    /**
     * deployStarting - Triggered before deployment begins
     */
    deployStarting("DEPLOY_STARTING"),

    /**
     * deployCompleted - Triggered when an application deployment is completed
     */
    deployCompleted("DEPLOY_COMPLETED"),

    /**
     * deployStarted - Triggered before a cut over starts
     */
    cutoverStarting("CUTOVER_STARTING"),

    /**
     * deployCompleted - Triggered when an cut over is completed
     */
    cutoverCompleted("CUTOVER_COMPLETED"),

    /**
     * preReleaseVerificationStarted =  that occur when a quality check to ensure an application
     * has met all criteria for a production release is initialized
     */
    preReleaseVerificationStarting("PRE_RELEASE_VERIFICATION_STARTING"),

    /**
     * preReleaseVerificationCompleted =  that occur when a quality check to ensure an application
     * has met all criteria for a production release is initialized
     */
    preReleaseVerificationCompleted("PRE_RELEASE_VERIFICATION_COMPLETED"),

    /**
     * releaseStarted - Fired when a production release has started
     */
    releaseStarting("RELEASE_STARTING"),

    /**
     * releaseCompleted - Fired when a production release has started
     */
    releaseCompleted("RELEASE_COMPLETED"),

    /**
     * releaseFinalized - Fired when a production release has finalized
     */
    releaseFinalized("RELEASE_FINALIZED"),

    /**
     * qualityProfileStarting - Fired when a quality profile is started
     */
    qualityProfileStarting("QUALITY_PROFILE_STARTING"),

    /**
     * qualityProfileCompleted - Fired when a quality profile is completed
     */
    qualityProfileCompleted("QUALITY_PROFILE_COMPLETED"),

    /**
     * alertSent
     */
    alertSent("ALERT_SENT"),

    approvalRequestSent("APPROVAL_REQUEST_SENT"),

    pipelineCompleted("PIPELINE_COMPLETED"),
    testExecutionStarting("TEST_EXECUTION_STARTING"),
    testExecutionCompleted("TEST_EXECUTION_COMPLETED")


    private PipelineEventType(String name) {
        this.name = name
    }
    final String name
}
