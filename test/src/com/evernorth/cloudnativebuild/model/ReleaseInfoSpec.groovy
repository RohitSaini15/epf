package com.evernorth.cloudnativebuild.model

import com.evernorth.cloudnativebuild.model.ReleaseInfo
import spock.lang.Specification

class ReleaseInfoSpec extends Specification{
    def "ReleaseInfo.toString returns a string"(){
        setup:
        ReleaseInfo info = new ReleaseInfo()
        when:
        def str = info.toString()
        then:
        str!=null
        str!=""
    }
}
