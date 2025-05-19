package com.evernorth.cloudnativebuild.data

/**
 * Contains constants defined in the Jenkins DSL outside the pipeline
 */
class PipelineConstants {
    static final String BUILD_RESULT_FAILURE = "FAILURE"
    static final String BUILD_RESULT_UNSTABLE = "UNSTABLE"
    static final String BUILD_RESULT_SUCCESS = "SUCCESS"
    //static final String BUILD_RESULT_ABORTED = "ABORTED"
    static final String BUILD_RESULT_NOT_BUILT = "NOT_BUILT"
    static final String COMMON_MODULE_TEMPLATE = "common"

    // STASH NAMES
    static final String STASH_SOURCE_FULL = "source-stash"
    //static final String STASH_ARTIFACTS = "artifact-stash"
    static final String STASH_RELEASE_FILES = "release-stash"

    static final String LOG_FILE_POSTFIX = "-results.json"
    static final String ERROR_SEP = "*" * 133 + System.getProperty("line.separator")

    // METADATA
    static final String RELEASE_METADATA_FILE= "releaseInfo.json"

    // ARTIFACT Types
    static final String ARTIFACT_TYPE_ARCHIVE = "archive"
    static final String ARTIFACT_TYPE_GIT_TAG = "git_tag"
    static final String ARTIFACT_TYPE_CONTAINER = "container"

    static final String DEFAULT_DEPLOYABLE_BRANCHES="^develop\$,^release.*,^hotfix.*"

    // Execution Schedule Types Map
    static final Map METHOD_SCHEDULE_MAP = [
        "executePreReleaseClosure": PRERELEASE_BUILD,
        "executeReleaseClosure": CALLBACK_BUILD
    ]
    // StepInvocation buildSchedule Types
    public static String CURRENT_BUILD="CURRENT_BUILD"
    public static String CALLBACK_BUILD="CALLBACK_BUILD"
    public static String PRERELEASE_BUILD="PRERELEASE_BUILD"

    // verbs
    static final String VERB_AWAIT = "awaitapproval"
    static final String VERB_RELEASE = "release"
    static final String VERB_PRERELEASE = "prerelease"
    static final String VERB_RUN_SCRIPT = "runscript"
    static final String VERB_DEPLOY = "deploy"
    static final String VERB_BUILD = "build"
    
    // git cred IDs (chosen based on git URL)
    static final String HS_GIT_CRED = '81fbd2fe-c892-472c-9a20-6a327b2960d3'
    static final String CIGNA_GIT_CRED = '89b25140-4e3e-4b59-b3e5-2954781c574e'
    static final String CLOUD_GIT_CRED = 'ghec-sa-pat-uname-pwd'

    static final String HS_GIT_TOKEN = 'GIT_TOKEN'
    static final String CIGNA_GIT_TOKEN = 'GIT_TOKEN'
    static final String CLOUD_GIT_TOKEN = 'ghec-sa-repo-pat-secret'
}
