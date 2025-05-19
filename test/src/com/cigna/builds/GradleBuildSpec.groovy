package com.cigna.builds

import com.cigna.SinglePodTest

class GradleBuildSpec extends SinglePodTest {
    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
        //currently the pipeline is setting the GRADLE_USER_HOME to a literal string 'null', so adding this as part of
        //test cases so that it is handled properly in code
        script.env.GRADLE_USER_HOME='null'
    }

    final Map sonarQubeMap = [:]
    
    def '''if publish is present, it must have a valid value'''() {
        given:
        def gradleBuild = new GradleBuild(
            config: [checkmarxEnabled: false, sonarEnabled: false] + publishOpt,
            script: script,
            psc: psc)
        when:
        def validateOutcome = gradleBuild.validate()
        then:
        validateOutcome.size() == expectedSize
        assert check ? check(validateOutcome) : true
        
        where:
        publishOpt << [[:], [publish: ''], [publish: 'none'], [publish: 'invalid input']]
        expectedSize << [0, 0, 0, 1]
        check << [null, null, null, { it[0] =~ /'publish' setting .* 'invalid input'\..*all, none/ }]
    }

    def """When executeBuildAndTest stage is called, gradle build with properties"""() {
        given:
            def gradleBuild = new GradleBuild(
                config: [:],
                script: script, 
                psc: psc)
        when:
            gradleBuild.executePreBuildStage()
            gradleBuild.executeBuildAndTestStage()
        then:
            1 * getPipelineMock("sh")('gradle clean build')
    }

    def """When executeBuildAndTest stage is called and gradle wrapper enabled, gradlew build with properties"""() {
        given:
            def gradleBuild = new GradleBuild(
                    config: [
                            gradle: [
                                    gradleWrapperScript: './gradlew'
                            ]
                    ],
                    script: script,
                    psc: psc
            )
        when:
            gradleBuild.executePreBuildStage()
            gradleBuild.executeBuildAndTestStage()
        then:
            1 * getPipelineMock("sh")("chmod +x ./gradlew")
            1 * getPipelineMock("sh")("./gradlew clean build")
    }

    def """When sonarQube.maxContainerMemory is not set, the default value is utilized"""() {
        given:
            def gradleBuild = new GradleBuild(
                config: [:],
                script: script,
                psc: psc
            )
        when:
            gradleBuild.init()
            gradleBuild.prePodConfig()
            gradleBuild.postPodConfig()
        then:
            assert gradleBuild.basePodConfig.containers.find{it.name == gradleBuild.sonarContainerName}.resources.limits.memory == GradleBuild.DEFAULT_MAX_CONTAINER_MEMORY
    }

    def """When sonarQube.maxContainerMemory are set, the specified options are utilized"""() {
        given:
            def gradleBuild = new GradleBuild(
                config: [
                        sonarQube  : [
                                scannerOptions    : '-Xmx2048m -XX:MaxPermSize=1024m',
                                containerMaxMemory: '3Gi'
                        ],
                        artifactory: [
                                credentialsId: 'test-creds'
                        ]
                ], 
                script: script,
                psc: psc
            )
        when:
            gradleBuild.init()
            gradleBuild.prePodConfig()
            gradleBuild.postPodConfig()
        then:
            def sonarConfig = gradleBuild.basePodConfig.containers.find { it.name == gradleBuild.sonarContainerName }
            assert sonarConfig.env.find { it.name == 'SONAR_SCANNER_OPTS' }.value == '-Xmx2048m -XX:MaxPermSize=1024m'
            assert sonarConfig.resources.limits.memory == '3Gi'
    }

    def """When executePublishStage is called with various publish options that will not generate a publish phase"""() {
        given:
            def gradleBuild = Spy(new GradleBuild(
                config: [
                        artifactory: [
                                credentialsId: 'test-creds'
                        ],
                        gradle: [
                                gradleWrapperScript: './gradlew'
                        ],
                        releaseBranchPattern: 'release-branch',
                        publish: publishOption
                ],
                script: script,
                psc: psc
            ))   
            gradleBuild.getVersionFromGradle() >> currentVersion
            gradleBuild.gitBranch() >> currentBranch
        when:
            gradleBuild.executePreBuildStage()
            gradleBuild.publish()
        then:
            1 * getPipelineMock('echo')({ it.contains("Project version/branch name does not match 'publish' property") })
        where:
            currentBranch << ["release-branch", "feature-branch", "release-branch", "feature-branch", "release-branch"]
            currentVersion << ["1.0.0-SNAPSHOT", "1.0.0", "1.0.0", "1.0.0", "1.0.0-SNAPSHOT"]
            publishOption << ["releases", "snapshots", "none", null, null]
    }
    
    def '''publish is skipped when publishEnabled if false'''() {
        given:
        def gradleBuild = new GradleBuild(
            config: [branchPattern: '.*'] + publishOpt,
            script: script,
            psc: psc
        )
        when:
        gradleBuild.publish()
        then:
        echoTimes * getPipelineMock('echo')({ it.startsWith('publishEnabled is false')})
        shTimes * getPipelineMock('sh')({ it.startsWith('gradle publish')})
        
        where:
        publishOpt << [[:], [publishEnabled: true], [publishEnabled: false]]
        echoTimes << [0, 0, 1]
        shTimes << [1, 1, 0]
    }
    
    def """When executePublishStage is called with various publish options that will generate a publish phase"""() {
        given:
            def gradleBuild = Spy(new GradleBuild(
                config: [
                        artifactory: [
                                credentialsId: 'test-creds'
                        ],
                        gradle: [
                                gradleWrapperScript: './gradlew'
                        ],
                        releaseBranchPattern: 'release-branch',
                        publish: publishOption
                ],
                script: script,
                psc: psc
            ))
            explicitlyMockPipelineVariable("ARTIFACTORY_USER")
            explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
            gradleBuild.getVersionFromGradle() >> currentVersion
            gradleBuild.gitBranch() >> currentBranch
        when:
            gradleBuild.executePreBuildStage()
            gradleBuild.executeBuildAndTestStage()
            gradleBuild.publish()
        then:
            1 * getPipelineMock("sh")('chmod +x ./gradlew')
            1 * getPipelineMock("sh")('./gradlew clean build')
            1 * getPipelineMock("sh")('./gradlew publish -PmavenUsername=$ARTIFACTORY_USER -PmavenPassword=$ARTIFACTORY_APIKEY')
        where:
            currentBranch << ['feature-branch', 'release-branch', 'feature-branch', 'release-branch', 'release-branch']
            currentVersion << ['1.0.0-SNAPSHOT', '1.0.0', '1.0.0-SNAPSHOT', '1.0.0', '']
            publishOption << ['all', 'releases', 'snapshots', null, null]
    }
    
    def """When buildTasks is specified, it will override the gradle task list similar to the linting phase."""() {
        given:
            def gradleBuild = new GradleBuild(
                    config: [
                            gradle: [
                                    buildTasks: buildTasks,
                                    gradleWrapperScript: './gradlew'
                            ]
                    ],
                    script: script,
                    psc: psc
            )
        when:
            gradleBuild.executePreBuildStage()
            gradleBuild.executeBuildAndTestStage()
        then:
            1 * getPipelineMock("sh")("chmod +x ./gradlew")
            1 * getPipelineMock("sh")(gradleCommand)
        where:
            buildTasks << ["clean test", "test"]
            gradleCommand << ["./gradlew clean test", "./gradlew test"]
    }
    
    def """When publishTasks is specified, it will override the gradle task list similar to the linting phase and will work
with either a collection or a string."""() {
        given:
        def gradleBuild = new GradleBuild(
                config: [
                        artifactory: [
                                credentialsId: 'test-creds'
                        ],
                        gradle: [
                                publishTasks: publishTasks,
                                gradleWrapperScript: './gradlew'
                        ],
                        publish: 'all'
                ],
                script: script,
                psc: psc
        )
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        when:
            gradleBuild.executePreBuildStage()
            gradleBuild.publish()
        then:
            1 * getPipelineMock("sh")("chmod +x ./gradlew")
            1 * getPipelineMock("sh")(gradleCommand)
        where:
            publishTasks << [["clean", "publish"], "publishAllPublicationsToMavenRepository"]
            gradleCommand << ['./gradlew clean publish -PmavenUsername=$ARTIFACTORY_USER -PmavenPassword=$ARTIFACTORY_APIKEY', './gradlew publishAllPublicationsToMavenRepository -PmavenUsername=$ARTIFACTORY_USER -PmavenPassword=$ARTIFACTORY_APIKEY']
    }
    
    def """When the various options for gradle user home are set, ensure that the proper value is selected and default is to not send a command line value"""() {
        given:
            def gradleBuild = new GradleBuild(
                config: [
                    gradle: [
                        gradleUserHome: gradleUserHome,
                        extraArgs: extraArgs
                    ]
                ],
                script: script,
                psc: psc)
            script.env.GRADLE_USER_HOME=envGradleUserHome
        when:
            gradleBuild.executePreBuildStage()
            gradleBuild.executeBuildAndTestStage()
        then:
            1 * getPipelineMock("sh")("gradle${expectedCommandLine} clean build")
        where:
            gradleUserHome << ['testGradleUserHome', 'testGradleUserHome', null, null]
            extraArgs << ['','','extra','']
            envGradleUserHome << ['envGradleUserHome', null, 'envGradleUserHome', null]
            expectedCommandLine << [' -g testGradleUserHome', ' -g testGradleUserHome', ' extra -g envGradleUserHome', '']
    }
    
    def '''getVersionFromGradle returns a version string or null'''() {
        given:
        def gradleBuild = new GradleBuild(
            script: script,
            psc: psc
        )
        when:
        def actual = gradleBuild.getVersionFromGradle()
        then:
        1 * getPipelineMock('sh')([script: 'gradle properties', returnStdout: true]) >> propertyOutput
        actual == expectedReturn
        
        where:
        propertyOutput << ['',
                           'version: 1.2.3',
                           'abc\nversion: 1.0-alpha   \ndef',
                           'version:    1.2.4 \nmore stuff',
                           'more stuff\nversion: 1.2.5-SNAPSHOT']
        expectedReturn << [null, '1.2.3', '1.0-alpha', '1.2.4', '1.2.5-SNAPSHOT']
    }
}