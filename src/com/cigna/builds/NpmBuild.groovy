package com.cigna.builds

/**
 * For builds utilizing NPM (Javascript).
 */
class NpmBuild extends NodeBuild {
    NpmBuild() {
        containerName = 'nodevlts-alpine'
        containerImage = 'enterprise-devops/node'
        containerVersion = 'lts-alpine'
        utility = 'npm'
        stashIncludePattern = ''
        checkForTypeScript = true
    }

}
