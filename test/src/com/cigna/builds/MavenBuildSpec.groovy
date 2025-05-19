package com.cigna.builds

import com.cigna.SinglePodTest

class MavenBuildSpec extends SinglePodTest {
    
    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    final Map sonarQubeMap = [:]
    
    def '''shouldRunBuild is true unless explicitly disabled using mavenBuildEnabled or buildEnabled'''() {
        given:
        def mavenBuild = new MavenBuild(
            config: config,
            script: script,
            psc: psc
        )
        
        expect:
        mavenBuild.shouldRunBuild() == whereBuild
        
        where:
        config << [
            [mavenBuildEnabled: false],
            [mavenBuildEnabled: true, buildEnabled: false],
            [buildEnabled: false],
            [:]
        ]
        whereBuild << [false, true, false, true]
    }
    
    def '''shouldRunPublish is true unless explicitly or both the (release)branchPattern don't match
           and hasSnapshotVersion isn't true'''() {
        given:
        def mavenBuild = new MavenBuild(
            config: config,
            script: script,
            psc: psc
        )
        
        expect:
        mavenBuild.shouldRunPublish() == wherePublish
        
        where:
        config << [
            [mavenPublishEnabled: false],
            [publishEnabled: false],
            [publishEnabled: true, releaseBranchPattern: 'not-master'],
            [publishEnabled: true, releaseBranchPattern: 'not-master', branchPattern: 'master'],
            [publishEnabled: true, branchPattern: 'not-master'],
            [publishEnabled: true, releaseBranchPattern: 'not-master', hasSnapshotVersion: true],
            [branchPattern: '.*'],
        ]
        wherePublish << [false, false, false, false, false, true, true]
    }

    def """When executeBuildAndTest stage is called, the configFileProvider.call retrieves and binds
           the Maven Settings path to a variable and mvn clean package is called with appropriate flags"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ],
            script: script,
            psc: psc)
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        mavenBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("configFileProvider.call")(*_)
        1 * getPipelineMock("configFile.call")(*_)
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* clean verify/ })
    }

    def """When the maven.debug flag is true, the 'mavenDebug()' method is called in addition to the default behavior"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                maven      : [
                    debug: true
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ],
            script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")

        mavenBuild.executeBuildAndTestStage()

        then:
        2 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* help:effective-(settings|pom) .*/ })
        2 * getPipelineMock("sh")({ it ==~ /cat effective-(settings|pom).xml/ })
        2 * getPipelineMock("configFileProvider.call")(*_)
        2 * getPipelineMock("configFile.call")(*_)
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* clean verify/ })
        2 * getPipelineMock("withCredentials")(*_)
    }

    def """ When executePublishStage is called, configFileProvider.call and withCredentials are called
            and maven deploy is called with artifactory credentials"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ],
            script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        mavenBuild.executePublishStage()
        then:
        1 * getPipelineMock("configFileProvider.call")(*_)
        1 * getPipelineMock("configFile.call")(*_)
        1 * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* deploy .*/ })
    }

    def """When executePublishStage is called and hasSnapshotVersion is true, the release plugin is invoked in addition to publish"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                artifactory         : [
                    credentialsId: 'test-creds'
                ],
                maven               : [
                    pathToPom: 'my-parent/pom.xml'
                ],
                branchPattern       : 'master',
                releaseBranchPattern: releaseBranchPatternRegex,
                hasSnapshotVersion  : hasSnapshotVersion,
            ],
            script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        script.scm.branches = [[name: 'master']]
        mavenBuild.executePreBuildStage()
        mavenBuild.executePublishStage()
        then:
        1 * getPipelineMock("configFileProvider.call")(*_)
        1 * getPipelineMock("configFile.call")(*_)
        1 * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("sh")({
            it ==~ expectedPattern
        })
        where:
        releaseBranchPatternRegex << ["master", "not_master", ".*", "master"]
        expectedPattern << [
            /mvn -B -s .* -f 'my-parent\/pom.xml' .* release:prepare release:perform .*/,
            /mvn -B -s .* -f 'my-parent\/pom.xml' .* deploy .*/,
            /mvn -B -s .* -f 'my-parent\/pom.xml' .* release:prepare release:perform .*/,
            /mvn -B -s .* -f 'my-parent\/pom.xml' .* deploy .*/,
        ]
        hasSnapshotVersion << [true, true, true, false]
    }

    def "Fail Validation when artifactory credentials id is missing"() {
        given:
        def mavenBuild = new MavenBuild(
            config: [
                checkmarx  : [
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'foo'
                    ],
                    credentialsId: 'bar'
                ],
                sonarQube  : [
                    credentialsId: 'baz'
                ],
                artifactory: [
                    foo: 'bar'
                ]
            ],
            script: script,
            psc: psc
        )

        when:
        def issues = mavenBuild.validate()

        then:
        // assert issues.size() > 0
        assert issues.contains(
            'Missing required Maven Build specification: artifactory.credentialsId'
        )

    }

    def "Succeed Validation when artifactory credentials id is provided"() {
        given:
        def mavenBuild = new MavenBuild(
            config: [
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ],
            script: script,
            psc: psc
        )

        when:
        def issues = mavenBuild.validate()

        then:
        assert !issues.contains('Missing Artifactory Credentials')

    }

    def """When executeBuildAndTest stage is called with pathToPom, the configFileProvider.call retrieves and binds
           the Maven Settings path to a variable and mvn clean package is called with appropriate flags"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                logLevel   : 'OFF',
                maven      : [
                    pathToPom: 'my-parent/pom.xml'
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ],
                sonarQube  : sonarQubeMap
            ], script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        mavenBuild.executePreBuildStage()
        mavenBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("configFileProvider.call")(*_)
        1 * getPipelineMock("configFile.call")(*_)
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* -f 'my-parent\/pom.xml' .* clean verify/ })
    }

    def """ When executePublishStage is called with pathToPom, configFileProvider.call and withCredentials are called
            and maven deploy is called with artifactory credentials"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                logLevel   : 'OFF',
                maven      : [
                    pathToPom: 'my-parent/pom.xml'
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ],
                sonarQube  : sonarQubeMap
            ], script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        mavenBuild.executePreBuildStage()
        mavenBuild.executePublishStage()
        then:
        1 * getPipelineMock("configFileProvider.call")(*_)
        1 * getPipelineMock("configFile.call")(*_)
        1 * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* -f 'my-parent\/pom.xml' .* deploy .*/ })
    }

    def """ When executePublishStage is called with enforcer is true, configFileProvider.call and withCredentials are called
            and maven deploy is called with artifactory credentials"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                maven      : [
                    enforcer     : true,
                    enforcerRules: 'alwaysPass'
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ], script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        mavenBuild.executeBuildAndTestStage()
        then:
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce .* -Drules=alwaysPass -Dmaven.repo.local.*/ })
        2 * getPipelineMock("configFileProvider.call")(*_)
        2 * getPipelineMock("configFile.call")(*_)
        2 * getPipelineMock("withCredentials")(*_)
    }

    def """ When executePublishStage is called with enforcer is true, and custom ruleset, configFileProvider.call and withCredentials are called
            and maven deploy is called with artifactory credentials"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                maven      : [
                    enforcer     : true,
                    enforcerRules: 'bannedDependencies,bannedRepositories,banTransitiveDependencies,dependencyConvergence'
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ], script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        mavenBuild.executeBuildAndTestStage()
        then:
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce .* -Drules=bannedDependencies,bannedRepositories,banTransitiveDependencies,dependencyConvergence .*/ })
        2 * getPipelineMock("configFileProvider.call")(*_)
        2 * getPipelineMock("configFile.call")(*_)
        2 * getPipelineMock("withCredentials")(*_)
    }

    def """ When executePublishStage is called with enforcer = false, and custom ruleset, configFileProvider.call and withCredentials are called
            and maven deploy is called with artifactory credentials"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                maven      : [
                    enforcer     : false,
                    enforcerRules: 'bannedDependencies,bannedRepositories,banTransitiveDependencies,dependencyConvergence'
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ], script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        mavenBuild.executeBuildAndTestStage()
        then:
        1 * getPipelineMock("configFileProvider.call")(*_)
        1 * getPipelineMock("configFile.call")(*_)
    }

    def """When maven is called with authSettings set to true, conduit uses the auth-settings.xml file"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                logLevel   : 'OFF',
                maven      : [
                    authSettings: true
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ],
                sonarQube  : sonarQubeMap
            ], script: script,
            psc: psc
        )
        mavenBuild.executePreBuildStage()

        then:
        assert mavenBuild.mavenSettingsId == MavenBuild.MAVEN_AUTH_SETTINGS_CONFIG_ID
    }

    def """When maven is called with authSettings set to false, conduit uses the settings.xml file"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                logLevel   : 'OFF',
                maven      : [
                    authSettings: false
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ],
                sonarQube  : sonarQubeMap
            ], script: script,
            psc: psc
        )
        mavenBuild.executePreBuildStage()

        then:
        assert mavenBuild.mavenSettingsId == MavenBuild.MAVEN_SETTINGS_CONFIG_FILE_ID
    }

    def """When maven is called with authSettings undefined, conduit uses the settings.xml file"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                logLevel   : 'OFF',
                artifactory: [
                    credentialsId: 'test-creds'
                ],
                sonarQube  : sonarQubeMap
            ], script: script,
            psc: psc
        )
        mavenBuild.executePreBuildStage()

        then:
        assert mavenBuild.mavenSettingsId == MavenBuild.MAVEN_SETTINGS_CONFIG_FILE_ID
    }

    def """When sonarQube.maxContainerMemory is not set, the default value is utilized"""() {
        when:
        def mavenBuild = new MavenBuild(
            config: [
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ], script: script,
            psc: psc
        )
        mavenBuild.prePodConfig()
        mavenBuild.postPodConfig()

        then:
        assert mavenBuild.basePodConfig.containers.find { it.name == mavenBuild.sonarContainerName }.resources.limits.memory == MavenBuild.DEFAULT_MAX_CONTAINER_MEMORY
    }

    def """When sonarQube.maxContainerMemory are set, the specified options are utilized"""() {
        when:

        def mavenBuild = new MavenBuild(
            config: [
                sonarQube  : [
                    scannerOptions    : '-Xmx2048m -XX:MaxPermSize=1024m',
                    containerMaxMemory: '3Gi'
                ],
                artifactory: [
                    credentialsId: 'test-creds'
                ]
            ], script: script,
            psc: psc
        )
        mavenBuild.prePodConfig()
        mavenBuild.postPodConfig()

        def sonarContainer = mavenBuild.basePodConfig.containers.find { it.name == mavenBuild.sonarContainerName }
        then:
        assert sonarContainer.env.find { it.name == 'SONAR_SCANNER_OPTS' }.value == '-Xmx2048m -XX:MaxPermSize=1024m'
        assert sonarContainer.resources.limits.memory == '3Gi'
    }
}