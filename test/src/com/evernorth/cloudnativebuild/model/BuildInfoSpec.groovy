package com.evernorth.cloudnativebuild.model

import com.evernorth.cloudnativebuild.model.BuildInfo
import spock.lang.Specification

class BuildInfoSpec extends Specification {
    def "BuildInfo toString returns string"(){
        setup:
        BuildInfo buildInfo = new BuildInfo()
        buildInfo.nodeName="findme"
        when:
        String buildString = buildInfo.toString()
        then:
        buildString.indexOf("findme")>-1
    }
}
