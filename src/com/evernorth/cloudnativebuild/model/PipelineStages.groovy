package com.evernorth.cloudnativebuild.model

enum PipelineStages {
    PRE_CHECK_OUT,
    SCM_CHECKOUT,
    PRE_STASH,
    PRE_BUILD,
    BUILD,
    PRE_RELEASE,
    PACKAGE,
    CONTAINER,
    DEPLOY,
    RELEASE,
    FINALIZE_RELEASE,
    POST_RELEASE,
    ALL
}
