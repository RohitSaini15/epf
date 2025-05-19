package com.cigna.common.phases

import com.cigna.common.exception.InvalidInputException
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.DockerUri

class ContainerComparator implements Comparator {
    @NonCPS
    int compare(Object o1, Object o2) {
        def (containerOneImg, containerOneVersion) = discoverContainerDetails(o1 as Map)
        def (containerTwoImg, containerTwoVersion) = discoverContainerDetails(o2 as Map)

        return "$containerOneImg:$containerOneVersion" <=> "$containerTwoImg:$containerTwoVersion"
    }

    @NonCPS
    static List discoverContainerDetails(Map map) {
        String containerImg = '', containerVersion = ''
        if (map.containsKey('module')) {
            containerImg = map.module.image ?: ''
            containerVersion = map.module.version ?: ''
        }

        if (containerImg.empty && map.containsKey('container')) {
            containerImg = map.container.image ?: ''
            containerVersion = map.container.version ?: ''
        }
        if (containerImg.empty && map.containsKey('phaseInstance')) {
            containerImg = map.phaseInstance.containerImage ?: ''
            containerVersion = map.phaseInstance.containerVersion ?: ''
            return [containerImg, containerVersion] // some phases don't use containers...
        }
        if (containerImg.empty && map.containsKey('containerImage') && map.containsKey('containerVersion')) {
            containerImg = map.containerImage ?: ''
            containerVersion = map.containerVersion ?: ''
        }
        if (containerImg.empty && map.containsKey('image') && !( map.image instanceof Map )) {
            def result = DockerUri.imageAndTag(map.image)
            containerImg = result['image'] ?: ''
            containerVersion = result['tag'] ?: ''
        }

        if (containerImg.empty) {
            throw new InvalidInputException("Unable to determine image:version for $map")
        }
        [containerImg, containerVersion]
    }

    @NonCPS
    boolean equals(Object obj) {
        return this == obj
    }
}