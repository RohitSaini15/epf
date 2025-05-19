package com.cigna.deployment

import com.cigna.common.request.CurlRequestor

/**
 * Running Ucd Maven Version Import Cheat
 * This relies on a specific but re-usable implementation for Urban Code Integration with Maven
 * to support deploying both snapshot and release artifacts
 * the UCD component must have the Repository URL set to https://repo.sys.cigna.com/artifactory/${p:repoURL}
 * This means that the repoURL will be a property on the components
 * that will change where the UCD Import Process looks for the maven artifacts
 */
class RepoUrlUcdDeployment extends UcdDeployment {

    final String defaultProperty = 'repoURL'
    final String defaultPropertyValue1 = 'maven-snapshot-repos/'
    final String defaultPropertyValue2 = 'maven-release-repos/'
    protected CurlRequestor curlRequestor

    RepoUrlUcdDeployment() {
        additionalValidationItems = ['ucd.ucdEnvironment', 'ucd.credentialsId']

    }

    /*
     * Main entry method for deploy a phase.
     */

    @Override
    void deploy() {
        if (!curlRequestor) {
            curlRequestor = new CurlRequestor(psc, script)
        }
        curlRequestor.containerName = containerName
        ucdConfiguration = deploymentConfiguration.ucd
        urlBase = ucdUrlMap[(ucdConfiguration.get('ucdEnvironment', 'prod').toLowerCase())] + '/cli'
        ucdConfiguration.components.each { component ->
            setComponentProperty(component.name, defaultProperty, defaultPropertyValue1)
            versionImport(component)
            setComponentProperty(component.name, defaultProperty, defaultPropertyValue2)
            versionImport(component)
        } // end of component
    }
}

