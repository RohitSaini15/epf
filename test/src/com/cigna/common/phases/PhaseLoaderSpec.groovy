package com.cigna.common.phases

import com.cigna.SinglePodTest
import com.cigna.deployment.PhasesDeployment
import com.cigna.deployment.PlzDeployment
import com.cigna.release.Release
import com.cigna.ticket.ServicenowTicket

class PhaseLoaderSpec extends SinglePodTest {
    def setup() {
        initScriptAndPsc()
    }

    def genericTicket = [
        ticketType                : 'Servicenow',
        title                     : 'SCT1234567',
        changeEnvironment         : 'dev',
        credentialsId             : 'snow-test-id',
        assigned_to               : 'testLanId',
        requested_by              : 'testLanId',
        cmdb_ci                   : 'test CI',
        u_emergency_contact_person: 'testLanId',
        u_emergency_contact_number: '212-555-1212'
    ]

    def "a ticket key on a deployment phase adds a ticketInstance to the phase"() {
        given:
        def phases = [
            [deploymentType : 'plz',
             branchPattern  : '.*',
             sdlcEnvironment: 'prod',
             ticket         : genericTicket]
        ]
        when:
        def pl = new PhaseLoader(script, psc).loadPhases(phases, null)
        then:
        //no issues
        pl[1].size() == 0
        // one phase with a phaseInstance and a ticket phaseInstance
        pl[0].size() == 1
        pl[0][0].'phaseInstance' instanceof PlzDeployment
        pl[0][0].ticket.phaseInstance instanceof ServicenowTicket
    }

    def "a release phase map creates a release phase"() {
        given:
        def phases = [
            [
                releaseType: 'preRelease',
                phases     : []
            ]
        ]
        when:
        def pl = new PhaseLoader(script, psc).loadPhases(phases, null)
        then:
        //no issues
        pl[1].size() == 0
        // one phase with a phaseInstance and a ticket phaseInstance
        pl[0].size() == 1
        pl[0][0].'phaseInstance' instanceof Release
    }

    def "a 'phases' deployment can load a ticket and multiple deploy phases"() {
        given:
        def phases = [
            [deploymentType : 'phases',
             branchPattern  : '.*',
             sdlcEnvironment: 'prod',
             ticket         : genericTicket,
             phases         : [
                 [deploymentType : 'plz',
                  branchPattern  : '.*',
                  sdlcEnvironment: 'prod'],
                 [moduleType            : 'docker',
                  branchPattern         : '.*',
                  sdlcEnvironment       : 'prod',
                  isProductionDeployment: true,
                  moduleName            : 'cnp-publish-image',
                  subCommand            : 'publishimage',
                  serviceAccount        : 'kaniko',
                  args                  : [sourceRepository            : 'registry-dev.cigna.com',
                                           sourceImage                 : 'test-source-image',
                                           sourceTag                   : 'test-source-tag',
                                           destinationRegistry         : 'registry-dev.cigna.com',
                                           destinationTag              : 'test-target-tag',
                                           publishOnNonDeployableBranch: true]]
             ]]
        ]
        when:
        def pl = new PhaseLoader(script, psc).loadPhases(phases, null)
        then:
        // no issues
        pl[1].size() == 0
        pl[0][0].phaseInstance instanceof PhasesDeployment
        pl[0][0].phases.every { it.containsKey('phaseInstance') }
        pl[0][0].ticket.phaseInstance instanceof ServicenowTicket
    }

    def '''validatePhaseEnabled returns the 'enabled' key or true for types with direct name correlation'''() {
        when:
        def phaseConfigTrue = [
            (type + 'Type')   : 'something',
            (type + 'Enabled'): true
        ]
        def phaseConfigFalse = [
            (type + 'Type')   : 'something',
            (type + 'Enabled'): false
        ]
        def phaseConfigMissing = [
            (type + 'Type'): 'something'
        ]
        then:
        PhaseLoader.validatePhaseEnabled(phaseConfigTrue)
        !PhaseLoader.validatePhaseEnabled(phaseConfigFalse)
        PhaseLoader.validatePhaseEnabled(phaseConfigMissing)
        where:
        type << ['build', 'deployment', 'ruleengine', 'test']
    }

    def '''validatePhaseEnabled is special-cased to align "freestyleType" with "lintingEnabled"'''() {
        when:
        def phaseConfigTrue = [
            freestyleType : 'something',
            lintingEnabled: true
        ]
        def phaseConfigFalse = [
            freestyleType : 'something',
            lintingEnabled: false
        ]
        def phaseConfigMissing = [
            freestyleType: 'something'
        ]
        then:
        PhaseLoader.validatePhaseEnabled(phaseConfigTrue)
        !PhaseLoader.validatePhaseEnabled(phaseConfigFalse)
        PhaseLoader.validatePhaseEnabled(phaseConfigMissing)
    }

    def '''validatePhaseEnabled always returns true for types that aren't "covered"'''() {
        when:
        def phaseConfigTrue = [
            (type + 'Type')   : 'something',
            (type + 'Enabled'): true
        ]
        def phaseConfigFalse = [
            (type + 'Type')   : 'something',
            (type + 'Enabled'): false
        ]
        def phaseConfigMissing = [
            (type + 'Type'): 'something'
        ]
        then:
        PhaseLoader.validatePhaseEnabled(phaseConfigTrue)
        PhaseLoader.validatePhaseEnabled(phaseConfigFalse)
        PhaseLoader.validatePhaseEnabled(phaseConfigMissing)
        where:
        type << ['module', 'frob']
    }

    def "injects global config options into phase config"() {
        given:
        def phase = [
            ticket             : [resourceStrategy: 'SMALL'],
            testing            : [[resourceScaleFactor: 0.5], [:]],
            resourceScaleFactor: 1
        ]
        Map parentConfig = [
            phaseCache: true,
            stashIncludePattern: 'include',
            stashExcludePattern: 'exclude',
            resourceStrategy   : 'MEDIUM',
            resourceScaleFactor: 2,
            cloudName          : 'my-cloud',
            checkmarxEnabled   : false,
            sonarEnabled       : false,
            metadataInArgs     : true,
            groupBy            : 'feature'
        ]

        // the content that is expected to be in all the maps, can have keys overridden
        def expectedInAll = parentConfig.subMap(['stashIncludePattern', 'stashExcludePattern',
                                                 'resourceStrategy', 'resourceScaleFactor', 'cloudName', 'groupBy']) + [phaseCache: true]

        when:
        def result = PhaseLoader.injectGlobalToPhaseConfig(phase, parentConfig)
        Map ticket = result.ticket as Map
        List<Map> testing = result.testing as List<Map>

        then:
        // + 2 because 1 for ticket, 1 for testing
        result.size() == parentConfig.size() + 2
        result.phaseCache == true
        result.stashIncludePattern == 'include'
        result.stashExcludePattern == 'exclude'
        result.resourceStrategy == 'MEDIUM'
        result.resourceScaleFactor == 1 // retained from the phase
        result.checkmarxEnabled == false
        result.sonarEnabled == false
        result.metadataInArgs == true
        result.groupBy == 'feature'
        result.cloudName == 'my-cloud'

        // resource strategy is always overridden based on parent config
        ticket == expectedInAll

        testing.size() == 2
        // can't add the `phase` members to the assertion because they get updated in place
        testing[0] == expectedInAll + [resourceScaleFactor: 0.5]
        testing[1] == expectedInAll
    }


    def "injectGlobalToPhaseConfig handles missing keys gracefully"() {
        given:
        Map<String, Object> phase = [:]
        def parentConfig = [
            phaseCache: true,
            stashIncludePattern: 'include',
            stashExcludePattern: 'exclude',
            resourceStrategy   : 'MEDIUM',
            resourceScaleFactor: 2,
            cloudName          : 'my-cloud'
        ]

        when:
        def result = PhaseLoader.injectGlobalToPhaseConfig(phase, parentConfig)

        then:
        // since phase is empty, the result should be the same as the parentConfig plus the defaults
        result == parentConfig + [checkmarxEnabled: true,
                                  sonarEnabled    : true]
    }

    def "phasesThatMatchPattern excludes phases that don't match the branch pattern"() {
        given:
        def config = [phases   : [
            [type: 'no branch pattern'],
            [branchPattern: '.*', type: 'any branch'],
            [branchPattern: 'main', type: 'main only']
        ],
                      cloudName: 'some-cloud']
        when:
        def result = PhaseLoader.phasesThatMatchPattern(script, config, [], 'feature/branch')
        then:
        result.size() == 2
        result.collect { it.type }.containsAll(['any branch', 'no branch pattern'])
        1 * getPipelineMock('echo')({ it.startsWith "Did not match branchPattern," })
    }

    def "phasesThatMatchPattern excludes phases that don't match the change pattern"() {
        given:
        def config = [phases   : [
            [type: 'no change pattern'],
            [changePattern: /.*\.py/, type: 'matching'],
            [changePattern: 'changelog.md', type: 'non-matching']
        ],
                      cloudName: 'some-cloud']
        when:
        def result = PhaseLoader.phasesThatMatchPattern(script, config, ['changed.py'], 'branchNameDoesntMatter')
        then:
        result.size() == 2
        result.collect { it.type }.containsAll(['matching', 'no change pattern'])
        1 * getPipelineMock('echo')({ it.startsWith "Did not match changePattern," })
    }

    def "phasesThatMatchPattern excludes phases that are disabled by config keys"() {
        given:
        def config = [phases   : [
            [buildType: 'no enabled key'],
            [buildEnabled: true, buildType: 'enabled'],
            [buildEnabled: false, buildType: 'disabled']
        ],
                      cloudName: 'some-cloud']
        when:
        def result = PhaseLoader.phasesThatMatchPattern(script, config, [], 'branchNameDoesntMatter')
        then:
        result.size() == 2
        result.collect { it.buildType }.containsAll(['enabled', 'no enabled key'])
    }

}
