package com.cigna.mocks

import com.evernorth.cloudnativebuild.mocks.JenkinsEnv
import com.evernorth.cloudnativebuild.mocks.Scm

class MockScript {

    def env = JenkinsEnv.build()

    def scm = new Scm()

    def container = {
        String container, Closure body -> 
            body()
    }
    
    def dir = {
        String dir, Closure body ->
            body()
    }

    def getCause() {
        return [
                cause           : [:],
                userName        : 'userName',
                userId          : 'userId',
                getUserId       : { 'userId' },
                shortDescription: 'upstream description'
        ]
    }

    def currentBuild = [
        result     : "SUCCESS",
        description: "",
        rawBuild   : [
            parent   : [
                parent: [
                    sources: [
                        [
                            source: [
                                repository: 'develop'
                            ]
                        ]
                    ]
                ]
            ],
            action   : [],
            getAction: { c -> ['getTotalCount': { 30 }, 'getFailCount': { 0 }, 'getSkipCount': { 1 }] },
            getCause : { getCause() }
        ]
    ]
}
