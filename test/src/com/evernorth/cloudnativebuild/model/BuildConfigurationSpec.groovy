package com.evernorth.cloudnativebuild.model

import com.evernorth.cloudnativebuild.data.PipelineConstants
import spock.lang.Specification

class BuildConfigurationSpec extends Specification {
    def "BuildConfiguration toString returns a Json string"() {
        setup:
        BuildConfiguration configuration = new BuildConfiguration()
        when:
        String str = configuration.toString()
        then:
        str != null
        str != ""
    }

    def "git credential gets set based on repoUrl"() {
        when:
        def url = mappings[0]
        def gitCred = mappings[1]
        then:
        BuildConfiguration.gitCredentialFromRepo(url) == gitCred
        where:
        mappings << [
                ['https://github.sys.cigna.com/cigna/some-repo', PipelineConstants.CIGNA_GIT_CRED],
                ['https://git.express-scripts.com/expressscripts/another-repo', PipelineConstants.HS_GIT_CRED],
                ['git@github.sys.cigna.com:cigna/blahblah.git', PipelineConstants.CIGNA_GIT_CRED],
                ['git@git.express-scripts.com:cigna/blahblah.git', PipelineConstants.HS_GIT_CRED],
                ['https://github.com/public/repo', PipelineConstants.CLOUD_GIT_CRED],
                ['git@github.com/public/repo', PipelineConstants.CLOUD_GIT_CRED],
                ['https://somegit.com/org/repo', PipelineConstants.CIGNA_GIT_CRED]
        ]
    }
}
