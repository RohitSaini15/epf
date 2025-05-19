package com.evernorth.cloudnativebuild.config

import com.cigna.SinglePodTest
import com.cigna.mocks.MockScript
import com.cigna.state.PipelineStateContext

class GlobalModuleManagerSpec extends SinglePodTest {
    def "Singleton created"() {
        given:
        def psc = new PipelineStateContext(new MockScript())
        when:
        def manager = psc.globalModuleManager.stateManager()
        def newManager = psc.globalModuleManager.stateManager()
        then:
        assert manager != null
        assert manager == newManager
    }
}
