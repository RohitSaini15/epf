package com.cigna.common.compliance

import com.cigna.SinglePodTest

class GoalsConfigSpec extends SinglePodTest {
    def setup() {
       initScriptAndPsc()
    }

    void '''that mixed case goal names are treated equally'''() {
        when:
        psc.goalsConfig.goalUpsert('Base')
        psc.goalsConfig.goalUpsert('base')
        psc.goalsConfig.goalUpsert('Attestation')
        psc.goalsConfig.goalUpsert('attestation')
        then:
        assert psc.goalsConfig.goals.size() == 2
    }

    def '''that mixed case goal names are treated equally when removing'''() {
        when:
        psc.goalsConfig.goalUpsert('Base')
        psc.goalsConfig.goalUpsert('base')
        psc.goalsConfig.goalUpsert('Attestation')
        psc.goalsConfig.goalUpsert('attestation')
        psc.goalsConfig.goalRemove('Base')
        then:
        assert psc.goalsConfig.goals.size() == 1
    }
    def '''that mixed case goal names are treated equally when comparing'''() {
        when:
        psc.goalsConfig.goalUpsert('base')
        psc.goalsConfig.goalUpsert('Attestation')

        then:
        assert psc.goalsConfig.goalExists('Base')
        assert psc.goalsConfig.goalExists('attestation')
    }
}
