package com.cigna.builds


import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class ReltioBuildSpec extends JenkinsPipelineSpecification {
    class Script {
        def env = [PATH: "stuff"]
    }
    def remoteConfigs =  [
            [
                    url: "https://git.sys.cigna.com/somecool_project/super_cool.git"
            ]
    ]
    def setup() {
        explicitlyMockPipelineVariable("scm")
        getPipelineMock("scm.getProperty")('userRemoteConfigs') >> remoteConfigs
        
        explicitlyMockPipelineStep('updateGitStatus')
    }

    def """When executeBuildAndTest stage is called, the ConfigFileProvider retrieves and binds
           the Reltio Settings path to a variable and mvn clean package is called with appropriate flags"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]
                ],
                script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("configFileProvider.call")(*_)
            1 * getPipelineMock("configFile.call")(*_)
            1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* clean verify/ })
    }

    def """When the reltio.debug flag is true, the 'reltioDebug()' method is called in addition to the default behavior"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [
                        debug: true
                    ],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]
                ],
                script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")

            reltioBuild.executeBuildAndTestStage()

        then:
            4 * getPipelineMock("echo")(*_)
            2 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* help:effective-(settings|pom) .*/ })
            2 * getPipelineMock("sh")({ it ==~ /cat effective-(settings|pom).xml/ })
            2 * getPipelineMock("configFileProvider.call")(*_)
            2 * getPipelineMock("configFile.call")(*_)
            1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* clean verify/ })
            2 * getPipelineMock("withCredentials")(*_)
    }

    def """ When executePublishStage is called, configFileProvider and withCredentials are called
            and reltio deploy is called with artifactory credentials"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]
                ],
                script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executePublishStage()

        then:
            1 * getPipelineMock("configFileProvider.call")(*_)
            1 * getPipelineMock("configFile.call")(*_)
            1 * getPipelineMock("withCredentials")(*_)
            1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* deploy .*/ })
    }

    def "Succeed Validation when artifactory credentials id is provided"() {
        given:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]
                ],
                script: script
            )

        when:
            def issues = reltioBuild.validate()

        then:
            assert !issues.contains('Missing Artifactory Credentials')
    }

    def """When executeBuildAndTest stage is called with pathToPom, the ConfigFileProvider retrieves and binds
           the Reltio Settings path to a variable and mvn clean package is called with appropriate flags"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    logLevel: 'OFF',
                    reltio: [
                        pathToPom: 'my-parent/pom.xml'
                    ],
                    artifactory: [
                        credentialsId: 'test-creds' 
                    ],
                ], script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executePreBuildStage()
            reltioBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("configFileProvider.call")(*_)
            1 * getPipelineMock("configFile.call")(*_)
            0 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* -f my-parent\/pom.xml .* clean verify/ })
    }

    def """ When executePublishStage is called with pathToPom, configFileProvider and withCredentials are called
            and reltio deploy is called with artifactory credentials"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    logLevel: 'OFF',
                    reltio: [
                        pathToPom: 'my-parent/pom.xml'
                    ],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ],
                ], script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executePreBuildStage()
            reltioBuild.executePublishStage()

        then:
            1 * getPipelineMock("configFileProvider.call")(*_)
            1 * getPipelineMock("configFile.call")(*_)
            1 * getPipelineMock("withCredentials")(*_)
            0 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* -f my-parent\/pom.xml deploy .*/ })
    }

    def """ When executePublishStage is called with enforcer is true, configFileProvider and withCredentials are called
            and reltio deploy is called with artifactory credentials"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [
                        enforcer: true,
                        enforcerRules: 'alwaysPass'   
                    ],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]
                ], script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executeBuildAndTestStage()

        then:
            2 * getPipelineMock("echo")(*_)
            1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce .* -Drules=alwaysPass -Dmaven.repo.local.*/ })
            2 * getPipelineMock("configFileProvider.call")(*_)
            2 * getPipelineMock("configFile.call")(*_)
            2 * getPipelineMock("withCredentials")(*_)
    }

    def """ When executePublishStage is called with alterVersions is true, configFileProvider and withCredentials are called
            and reltio deploy is called with artifactory credentials"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [
                        alterVersions: true 
                    ],  
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]        
                ],
                script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executeBuildAndTestStage()

        then:
            2 * getPipelineMock("echo")(*_)
            1 * getPipelineMock("sh").call("sed 's/COMMIT_ID/null/g' ./pom.xml > tmp.xml && mv tmp.xml ./pom.xml")
            2 * getPipelineMock("sh").call('pwd')
            2 * getPipelineMock("sh").call('ls -ltr')
            1 * getPipelineMock("sh").call('cd /home/jenkins/agent/workspace')
            1 * getPipelineMock("sh").call(*_)
            1 * getPipelineMock("sh").call('mvn -B -s Mock Generator for [RELTIO_SETTINGS]                                            -f ./pom.xml                                            -Drepo.mgr.env=\'null\'                                            -Dmaven.repo.local=\'/tmp/.cache/m2/repository\'                                                                                        clean verify')
            2 * getPipelineMock("usernamePassword.call").call(*_)        
            2 * getPipelineMock("configFileProvider.call")(*_)
            2 * getPipelineMock("configFile.call")(*_)
            2 * getPipelineMock("withCredentials")(*_)
    }

    def """ When executePublishStage is called with enforcer is true, and custom ruleset, configFileProvider and withCredentials are called
            and reltio deploy is called with artifactory credentials"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [
                        enforcer: true,
                        enforcerRules: 'bannedDependencies,bannedRepositories,banTransitiveDependencies,dependencyConvergence'
                    ],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]   
                ], script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executeBuildAndTestStage()

        then:
            2 * getPipelineMock("echo")(*_)
            1 * getPipelineMock("sh")({ it ==~ /mvn -B -s .* org.apache.maven.plugins:maven-enforcer-plugin:3.0.0-M3:enforce .* -Drules=bannedDependencies,bannedRepositories,banTransitiveDependencies,dependencyConvergence .*/ })
            2 * getPipelineMock("configFileProvider.call")(*_)
            2 * getPipelineMock("configFile.call")(*_)
            2 * getPipelineMock("withCredentials")(*_)
    }

    def """ When executePublishStage is called with enforcer = false, and custom ruleset, configFileProvider and withCredentials are called
            and reltio deploy is called with artifactory credentials"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    reltio: [
                        enforcer: false,
                        enforcerRules: 'bannedDependencies,bannedRepositories,banTransitiveDependencies,dependencyConvergence',
                    ],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ]
                ], script: script
            )
            explicitlyMockPipelineVariable("RELTIO_SETTINGS")
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            reltioBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("configFileProvider.call")(*_)
            1 * getPipelineMock("configFile.call")(*_)
    }

    def """When reltio is called with authSettings set to true, conduit uses the auth-settings.xml file"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    logLevel: 'OFF',
                    reltio: [
                        authSettings: true
                    ],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ],
                ], script: script
            )
            reltioBuild.executePreBuildStage()

        then:
            assert reltioBuild.reltioSettingsId == ReltioBuild.RELTIO_AUTH_SETTINGS_CONFIG_ID
    }

    def """When reltio is called with authSettings set to false, conduit uses the settings.xml file"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    logLevel: 'OFF',
                    reltio: [
                        authSettings: false
                    ],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ],
                ], script: script
            )
            reltioBuild.executePreBuildStage()

        then:
            assert reltioBuild.reltioSettingsId == ReltioBuild.RELTIO_SETTINGS_CONFIG_FILE_ID
    }

    def """When reltio is called with authSettings undefined, conduit uses the settings.xml file"""() {
        when:
            def script = new Script()
            def reltioBuild = new ReltioBuild(
                config: [
                    logLevel: 'OFF',
                    reltio: [],
                    artifactory: [
                        credentialsId: 'test-creds'
                    ],
                ], script: script
            )
            reltioBuild.executePreBuildStage()

        then:
            assert reltioBuild.reltioSettingsId == ReltioBuild.RELTIO_SETTINGS_CONFIG_FILE_ID
    }
    
}