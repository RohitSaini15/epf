package com.cigna.common.kubernetes

import com.cigna.common.utils.Utils
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import spock.lang.Specification

class PodAutotuningServiceSpec extends Specification {
    def client = Mock(KubernetesClient)
    def config = Mock(Config)
    def service = new PodAutotuningService('master-url', 'oauth-token')
    def script = new MockJenkins()

    def setup() {
        service.metaClass.newK8sClient = { ->
            client
        }
    }


    def '''testing converting Ki to Mi rounds accurately.'''() {
        when:
        def actualValue = Utils.normalizeToMi(originalValue)
        then:
        assert actualValue == expectedValue
        where:
        originalValue << ['1048576Ki', '213000Ki', '3013Ki', '500Ki']
        expectedValue << ['1024.00Mi', '208.01Mi', '2.94Mi', '0.49Mi']
    }


}