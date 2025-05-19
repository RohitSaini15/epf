package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.config.GlobalModuleManager
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import spock.lang.Specification

class CheckoutFromScmStepSpec extends Specification{

    MockJenkins jenkins
    PipelineStateContext psc
    def setup() {
        jenkins = new MockJenkins()
        psc = new PipelineStateContext(jenkins)

        FeatureFlags.scm.legacyCheckout =  true
    }

    def "checkout from SCM with trace logging returns true"(){
        jenkins.env.CNP_LOG_LEVEL="TRACE"
        jenkins.env.mockEnvironment.CNP_LOG_LEVEL="TRACE"
        setup:
        def step = new CheckoutFromScmStep(jenkins,  psc)
        when:
        Boolean result = step.execute()
        then:
        result
        jenkins.checkOutCount==1
    }
}
