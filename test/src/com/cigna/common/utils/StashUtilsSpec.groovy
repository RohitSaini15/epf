package com.cigna.common.utils

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.common.phases.PodSelector
import com.cigna.packaging.KanikoPackaging

class StashUtilsSpec extends SinglePodTest {
    Notification notification = Mock()

    def setup() {
        explicitlyMockPipelineVariable('quayToken')
        initScriptAndPsc()
    }

    def "test stash is called when multiple pods are detected"() {
        given:
        def block = [
            dockerRegistry: 'registry.cigna.com',
            image         : [
                name       : 'name',
                org        : 'org',
                buildArgs  : '',
                extraParams: ''
            ],
            quay          : [
                credentialsId: 'creds'
            ]
        ]
        script.env.GIT_COMMIT = 'abcd1234'
        def kanikoPackaging1 = new KanikoPackaging(config: [cloudName: '1', phaseCache: isCaching,] + block,
            script: script, notification: notification, psc: psc)
        def kanikoPackaging2 = new KanikoPackaging(config: [cloudName: cloudNameStr, phaseCache: isCaching,] + block,
            script: script, notification: notification, psc: psc)
        simulatePodTemplates(psc, [kanikoPackaging1, kanikoPackaging2], '1')
        when:
        kanikoPackaging1.run()

        then:
        numberOfCallsToStash * getPipelineMock('stash')(*_)

        where:
        numberOfCallsToStash << [1, 0]
        cloudNameStr << ['2', '1']
        isCaching << [true, false]
    }

    def 'unstashing only happens if STASH_ID is a non-empty string'() {
        given:
        def mockPodSel = GroovyMock(PodSelector)
        mockPodSel.hasMultiplePods() >> true
        psc.podSelector = mockPodSel
        script.env.STASH_ID = subjectStashId
        when:
        StashUtils.unstash(script, psc)
        then:
        unstashTimes * getPipelineMock('unstash')(*_)
        where:
        subjectStashId << [null, '', 'filler']
        unstashTimes << [0, 0, 1]
    }

    def 'when stash_id is non-empty, unstashing is only skipped if skipStashing feature flag is true and \
            podSelector doesnt have multiple pods'() {
        given:
        def mockPodSel = GroovyMock(PodSelector)
        mockPodSel.hasMultiplePods() >> multiPod
        psc.podSelector = mockPodSel
        script.env.STASH_ID = 'filler'
        when:
        def oldFlag = FeatureFlags.skipStashingWhenSinglePod
        FeatureFlags.skipStashingWhenSinglePod = skipStash
        StashUtils.unstash(script, psc)
        FeatureFlags.skipStashingWhenSinglePod = oldFlag
        then:
        unstashTimes * getPipelineMock('unstash')(*_)
        where:
        multiPod | skipStash | unstashTimes
        true     | true      | 1
        true     | false     | 1
        false    | true      | 0
        false    | false     | 1
    }

}
