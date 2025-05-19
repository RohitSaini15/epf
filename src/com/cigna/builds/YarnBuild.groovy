package com.cigna.builds

/**
 * For audit utilzing Yarn (Javascript).
 */
class YarnBuild extends NodeBuild {
    YarnBuild() {
        containerName = 'nodev12-alpine'
        containerImage = 'enterprise-devops/node'
        containerVersion = '12-alpine'
        stashIncludePattern = ''
        checkForTypeScript = true
        utility = 'yarn'
    }
}
