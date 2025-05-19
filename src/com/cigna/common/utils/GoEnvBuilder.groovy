package com.cigna.common.utils

import com.cloudbees.groovy.cps.NonCPS

/**
 * Class has been created to encapsulate all go specific environment variables and
 * consolidate to a single point of definition.
 */
class GoEnvBuilder {
    public static final String GOPROXY = 'https://cigna.jfrog.io/artifactory/api/go/go-repos,direct'
    public static final String GOSUMDB = 'off'
    public static final String GONOSUMDB = '*.jfrog.io,*.sys.cigna.com'
    public static final String GOPRIVATE = GONOSUMDB

    static String buildGoEnv(String goproxy = null, String goprivate = null) {
        "GOPRIVATE=${goprivate ?: GOPRIVATE} " +
            "GOPROXY=${goproxy ?: GOPROXY} GOSUMDB=${GOSUMDB} GONOSUMDB=${GONOSUMDB} "
    }

    @NonCPS
    static List<Map<String,String>> buildGoDeploymentEnv(List<Map<String,String>> envMap) {
        envMap.addAll ([
            [
                name : 'GO111MODULE',
                value: 'auto'
            ],
            [
                name : 'GOPROXY',
                value: GOPROXY
            ],
            [
                name : 'GOPRIVATE',
                value: GOPRIVATE
            ],
            [
                name : 'GOSUMDB',
                value: GOSUMDB
            ],
            [
                name : 'GONOSUMDB',
                value: GONOSUMDB
            ],
        ])
        envMap
    }
}
