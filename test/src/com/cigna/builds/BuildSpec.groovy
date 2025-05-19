package com.cigna.builds

import com.cigna.SinglePodTest

public class BuildSpec extends SinglePodTest {
    public class HttpMock {
        def getContent() {
            '{"results": {"uri": "test"}}'
        }
    }



    def setup() {
        explicitlyMockPipelineVariable('build')
        explicitlyMockPipelineVariable('addSonarProjectTags')
        explicitlyMockPipelineVariable('tagList')
        explicitlyMockPipelineVariable('runBuildAndTest')
        explicitlyMockPipelineVariable('runPublish')
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('withCredentials')
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineVariable('EPF_GITHUB_ACCESS_TOKEN')
        getPipelineMock('readProperties')(*_) >> [:]
        initScriptAndPsc()
    }

    def """When the releaseBranchPattern regular expression matches the current branch, which in
            this example is mocked to 'master', then currentBranchMatchesBranchPattern returns true
            otherwise false"""() {
        when:
        def build = new ValidBuild(
                config: [
                        branchPattern       : 'master',
                        releaseBranchPattern: releaseBranchPatternRegex
                ],
                script: script,
                psc: psc
        )

        then:
        build.currentBranchIsReleaseBranch() == valueOfCurrentBranchIsReleaseBranch

        where:
        releaseBranchPatternRegex << ["master", "not_master", ".*", '', null]
        valueOfCurrentBranchIsReleaseBranch << [true, false, true, true, true]
    }

    def "artifactoryApplicationNameScope correctly returns the scope"() {
        when:
        def build = new ValidBuild(
                config: [artifactory: [applicationName: "@cigna/cigna_gies_pipeline-test"]],
                script: script,
                psc: psc
        )
        def scope = build.artifactoryApplicationNameScope()

        then:
        scope == "cigna"
    }

    def """checkIfApplicationNameAndVersionUsedInArtifactory throws an exception when
            readJSON returns a map with contents in a results key"""() {
        when:
        def build = new ValidBuild(
                config: [artifactory: [applicationName: "@cigna/cigna_gies_pipeline-test"]],
                script: script,
                psc: psc
        )
        build.checkIfApplicationNameAndVersionUsedInArtifactory("test")

        then:
        1 * getPipelineMock("httpRequest")(*_) >> new HttpMock()
        1 * getPipelineMock("readJSON")(*_) >> [results: { uri: "stuff" }]
        def exception = thrown(Exception)
        exception.message == "An Artifactory package already exists with the same name, "\
                          + "and version as your target. Please increment the version, or change the "\
                          + "application name"
    }

    def """When warningsNG options are provided, usesWarningsNG returns the appropriate value"""() {
        when:
        def build = new ValidBuild(
                config: whenConfig,
                script: script,
                psc: psc
        )
        then:
        assert build.usesWarningsNG() == usesWarningsNG
        where:
        whenConfig << [
                [:],
                [warningsNG: [:]],
                [warningsNG: [param1: 'string', param2: ['list', 'of', 'strings'], param3: false]],
        ]
        usesWarningsNG << [
                false,
                true,
                true,
        ]
    }

    def """When warningsNG options are provided, recordIssues is called with the given parameters"""() {
        given:
        explicitlyMockPipelineStep('recordIssues')
        when:
        def build = new ValidBuild(
                config: whenConfig,
                script: script,
                psc: psc
        )
        build.publishWarningsNG()
        then:
        1 * getPipelineMock("recordIssues")(recordIssuesCalledWith)
        where:
        whenConfig << [
                [warningsNG: [:]],
                [warningsNG: [param1: 'string', param2: ['list', 'of', 'strings'], param3: false]],
        ]
        recordIssuesCalledWith << [
                [:],
                [param1: 'string', param2: ['list', 'of', 'strings'], param3: false],
        ]
    }


    def """When runScanningTools is called, it will analyze the config elements if the disable parameters
            were not provided for sonarqube and checkmarx, those will be executed normally"""() {
        when:
        def build = new ValidBuild(
                config: [
                        sonarEnabled    : whereSonar,
                        checkmarxEnabled: whereCheckmarx
                ],
                script: script,
                psc: psc
        )
        psc.complianceValidator.sonarEnabled = whereSonar
        psc.complianceValidator.checkmarxEnabled = whereCheckmarx

        build.runScanningTools()

        then:
        if (whereSonar) {
            assert build.scans.toMapString() ==~ /.*sonar.*/
        }
        if (whereCheckmarx) {
            assert build.scans.toMapString() ==~ /.*checkmarx.*/
        }

        where:
        whereSonar << [true, true, true, false]
        whereCheckmarx << [false, false, true, false]
    }

    def """shouldRunBuild is true unless buildEnabled is set to false"""() {
        when:
        def build = new ValidBuild(
            config: config,
            script: script,
            psc: psc
        )

        then:
        build.shouldRunBuild() == whereBuild

        where:
        config << [[buildEnabled: false], [buildEnabled: true], [:]]
        whereBuild << [false, true, true]
    }

    def """shouldRunPublish is true unless buildEnabled is set to false or (releaseBranchPattern ?: branchPattern) doesn't match branch name"""() {
        given:
        def build = new ValidBuild(
            config: config,
            script: script,
            psc: psc
        )

        expect:
        build.shouldRunPublish() == wherePublish

        where:
        config << [[publishEnabled: false],
                   [publishEnabled: true, branchPattern: '.*'],
                   [branchPattern: 'not-master'],
                   [releaseBranchPattern: 'not-master', branchPattern: '.*']]
        wherePublish << [false, true, false, false]
    }

    def '''when a sonar, checkmarx or git container is overridden via containers[] in config, the names are synced'''() {
        given:
        explicitlyMockPipelineStep('updateGitlabCommitStatus')
        def build = new ValidBuild(
                config: [
                    sonarEnabled    : sonarEnabled,
                    checkmarxEnabled: checkmarxEnabled,
                    buildType       : 'maven',
                    containers      : [
                        [
                            name : 'sonarv481-v1',
                            image: expectedSonarImage
                        ],
                        [
                            name : 'checkmarx-toolshackv105',
                            image: expectedCheckmarxImage
                        ],
                    ],
                    sonarQube       : [
                        credentialId: 'not-a-real-cred'
                    ],
                    checkmarx       : [
                        settings: [:]
                    ]
                ],
                script: script,
                psc: psc
        )

        when:
        simulatePodTemplate(psc, build)
        build.run()
        then:
        // mock a successful checkmarx run
        getPipelineMock('sh')({ it.script.startsWith('dso-cli checkmarx')}) >> 0

        build.sonarContainerName == expectedSonarName
        build.checkmarxContainerName == expectedCheckmarxName
        if (sonarEnabled) {
            build.basePodConfig.containers.find { it.name == expectedSonarName }.image == expectedSonarImage
        } else {
            build.basePodConfig.containers.count { it.name == expectedSonarName } == 0
        }
        if (checkmarxEnabled) {
            build.basePodConfig.containers.find { it.name == expectedCheckmarxName }.image == expectedCheckmarxImage
        } else {
            build.basePodConfig.containers.count { it.name == expectedCheckmarxName } == 0
        }
        where:
        sonarEnabled << [true, true, false]
        checkmarxEnabled << [true, true, false]
        expectedSonarName << ['sonarv50', 'sonarv481-v1', 'sonarv481-v1']
        expectedCheckmarxName << ['checkmarx-toolshackv121', 'checkmarx-toolshackv121', 'checkmarx-toolshackv121']
        expectedSonarImage << [
                'enterprise-devops/sonar:5.0',
                'enterprise-devops/sonar:4.8.1-v1',
                null
        ]
        expectedCheckmarxImage << [
                'dev-sec-ops/checkmarx-toolshack:1.2.1',
                'dev-sec-ops/checkmarx-toolshack:1.0.5',
                null
        ]

    }

    def '''when a sonar or checkmarx resources are overridden, they are respected in the pod template'''() {
        given:
        def build = new ValidBuild(
                config: [
                        buildType : 'maven',
                        containers: [
                                [
                                        name  : 'sonarv481-v1',
                                        memory: requestedSonarMemory,
                                ],
                                [
                                        name  : 'checkmarx-toolshackv121',
                                        memory: requestedCheckmarxMemory
                                ],
                        ]
                ],
                script: script,
                psc: psc
        )

        when:
        simulatePodTemplate(psc, build)
        then:
        build.basePodConfig.containers.size() == 3
        def sm = expectedSonarMemory
        def cm = expectedCheckmarxMemory
        build.basePodConfig.containers.find { it.name == 'sonarv481-v1' }.resources.limits.memory.toString() == sm
        build.basePodConfig.containers.find { it.name == 'checkmarx-toolshackv121' }.resources.limits.memory.toString() == cm
        where:
        requestedSonarMemory << [8000, 3000, 4000]
        requestedCheckmarxMemory << [4000, 2000, 1000]
        expectedSonarMemory << ['8000Mi', '3000Mi', '4000Mi']
        expectedCheckmarxMemory << ['4000Mi', '2000Mi', '1000Mi']
    }
}
