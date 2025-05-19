package com.cigna.common.naming

import com.cigna.builds.Build
import com.cigna.builds.DotnetcoreBuild
import com.cigna.deployment.Deployment
import com.cigna.packaging.KanikoPackaging
import com.cloudbees.groovy.cps.NonCPS

import static com.cigna.common.utils.Utils.calculateContainerName

class NameRegistry {
    @NonCPS
    static NameRegistry Instance() {
        new NameRegistry()
    }

    /**
     * provide some meaningful defaults so that we always have a viable mapped logical name, even if the owning phase
     * isn't in the list of phases to run.
     * It would be better if owning phases could register their own names dynamically, but if a phase isn't being
     * actively referenced in a pipeline, but another phase wants to know the default container name, the name wouldn't
     * be present in the name registry.
     */
    private Map<String, String> containerNames = [
            dotnetcore: calculateContainerName(DotnetcoreBuild.DEFAULT_CONTAINER_IMAGE,
                    DotnetcoreBuild.DEFAULT_CONTAINER_VERSION),
            sonar     : calculateContainerName(Build.sonarContainerImage, Build.sonarContainerVersion),
            checkmarx : calculateContainerName(Build.checkmarxContainerImage, Build.checkmarxContainerVersion),
            saml2aws : calculateContainerName(KanikoPackaging.SAML2AWS_CONTAINER_IMAGE, KanikoPackaging.SAML2AWS_CONTAINER_VERSION)
    ]

    @NonCPS
    void registerName(String logicalName, String actualName) {
        containerNames[logicalName] = actualName
    }

    String nameFor(String logicalName) {
        containerNames[logicalName]
    }

    Map<String, String> allNames() {
        containerNames
    }

    private NameRegistry() {}
}
