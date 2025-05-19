package com.cigna.packaging

import com.cigna.SinglePodTest
import com.cigna.common.exception.UnassignableTypeException
import com.cigna.common.notification.Notification


class PodmanPackagingSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    Notification notification = Mock()

    def setup() {
        explicitlyMockPipelineVariable('steps')
        initScriptAndPsc()
        script.env.GIT_COMMIT = 'abcdef1234567654321'
    }

    def """When build args are present but not a Map, throws Exception"""() {
        given:
        def packaging = new PodmanPackaging(
            config: [
                image: [
                    name     : 'name',
                    org      : 'org',
                    buildArgs: ['this', 'is', 'a', 'list']
                ],
                quay : [
                    credentialsId: 'creds'
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging)
        when:
        packaging.validate()
        then:
        Exception e = thrown UnassignableTypeException
        e.message.contains("Image build args")
    }

    def """When appropriate config items are defined, constructs image name and tag correctly"""() {
        given:
        def packaging = new PodmanPackaging(
            config: [
                image: [
                    name: 'name',
                    org : 'org'
                ],
                quay : [
                    credentialsId: 'creds'
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging)
        when:
        packaging.determineImageVars()
        then:
        packaging.imageName == 'org/name'
        packaging.imageTag == 'abcdef12'
    }

    def """When build args are provided, constructs command flags correctly"""() {
        given:
        def packaging = new PodmanPackaging(
            config: [
                image: [
                    name     : 'name',
                    org      : 'org',
                    buildArgs: [
                        key1: 'value1',
                        key2: 'value2'
                    ]
                ],
                quay : [
                    credentialsId: 'creds'
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging)
        when:
        String args = packaging.constructBuildArgsString()
        then:
        args == "--build-arg key1=\"value1\" --build-arg key2=\"value2\" "
    }

    def """When podmanBuildAndPushImage method is called, an echo is run, and the build and push
            script is run with Quay Credentials"""() {
        given:
        def packaging = new PodmanPackaging(
            config: [
                image: [
                    name     : 'name',
                    org      : 'org',
                    buildArgs: [
                        key1: 'value1',
                        key2: 'value2'
                    ]
                ],
                quay : [
                    credentialsId: 'creds'
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging)
        packaging.notification = notification

        when:
        explicitlyMockPipelineVariable('quayToken')
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        2 * getPipelineMock("echo").call(*_)
        1 * getPipelineMock("string.call").call(*_)
        2 * getPipelineMock("sh").call(*_)
        1 * notification.notifyWithAllMethods(*_)
    }

    def """When correct properties are set for ecr, podman auth is configured and podman
            command & build args are run for saml2aws"""() {
        setup:
        explicitlyMockPipelineVariable('AWS_PASSWORD')
        explicitlyMockPipelineVariable('AWS_USERNAME')

        when:
        def packaging = new PodmanPackaging(
            config: [
                dockerRegistry: '1234567.dkr.ecr.us-east-1.amazonaws.com',
                cloudName     : cloudName,
                image         : [
                    name       : 'name',
                    buildArgs  : whereBuildArgs,
                    extraParams: whereExtraParams
                ],
                ecr           : [
                    credentialsId: 'creds',
                    rolename     : 'test',
                    saml         : true,
                ]
            ], script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging, cloudName)
        packaging.notification = notification
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        2 * getPipelineMock("sh").call(*_)
        1 * getPipelineMock("sh").call('podman push --authfile=/home/jenkins/myfolder/.containers/auth.json 1234567.dkr.ecr.us-east-1.amazonaws.com/name:abcdef12')
        1 * getPipelineMock("echo").call("Building and pushing container image with build args: ${result}")
        1 * getPipelineMock("usernamePassword.call").call(['credentialsId': 'creds', 'usernameVariable': 'AWS_USERNAME', 'passwordVariable': 'AWS_PASSWORD'])
        1 * getPipelineMock("withCredentials").call(*_)
        1 * notification.notifyWithAllMethods(*_)

        where:
        result << ['--build-arg arg="matey" ', '', ''] // extraParams is not used in podman packaging and hence result is empty
        whereBuildArgs << [[arg: 'matey'], null, null]
        whereExtraParams << [null, '--ahoy there', null]
    }

    def """When dockerfile is defined, the -f flag is specified with the correct path"""() {
        given:
        def packaging = new PodmanPackaging(
            config: [
                dockerfile: dockerFileValue,
                image     : [
                    name     : 'name',
                    org      : 'org',
                    buildArgs: [
                        key1: 'value1',
                        key2: 'value2'
                    ]
                ],
                quay      : [
                    credentialsId: 'creds'
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging)
        packaging.notification = notification
        when:
        explicitlyMockPipelineVariable('quayToken')

        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        1 * getPipelineMock("sh")({ it ==~ /podman build ${dockerFileString}.*/ })
        1 * notification.notifyWithAllMethods(*_)
        where:
        dockerFileValue << ['somefile', '', null]
        dockerFileString << [
            '-f /home/jenkins/myfolder/somefile',
            '-f /home/jenkins/myfolder/Dockerfile',
            '-f /home/jenkins/myfolder/Dockerfile'
        ]
    }

    def """When PodmanPackaging.validatePhase is called with missing required config,
       a list of issues is returned"""() {
        given:
        def packaging = new PodmanPackaging(
            config: whereConfig,
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging)
        when:
        def validationIssues = packaging.validate()

        then:
        assert validationIssues.size() > 0
        where:
        whereConfig << [
            [branchPattern: 'master'],
            [branchPattern: 'master', image: [name: 'name', org: 'org']],
            [branchPattern: 'master', image: [name: 'name'], quay: [credentialsId: 'creds']],
            [branchPattern: 'master', image: [org: 'org'], quay: [credentialsId: 'creds']],
        ]
    }

    def """When PodmanPackaging.validatePhase is called with required config,
            validation returns empty list"""() {
        given:
        def packaging = new PodmanPackaging(
            config: [
                branchPattern: 'master',
                packagingType: 'podman',
                quay         : [
                    credentialsId: 'creds'
                ],
                image        : [
                    name     : 'name',
                    org      : 'org',
                    buildArgs: [
                        key1: 'value1',
                        key2: 'value2'
                    ]
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, packaging)
        when:
        def validationIssues = packaging.validate()

        then:
        assert validationIssues.isEmpty()
    }


    def """When buildContext is set, using quay, the context path is not the default."""() {
        given:
        def packaging = new PodmanPackaging(
            config: [
                buildContextPath: buildContextPathValue,
                image           : [
                    name: 'name',
                    org : 'org'
                ],
                quay            : [
                    credentialsId: 'creds'
                ]
            ],
            script: script,
            psc: psc
        )

        when:
        simulatePodTemplate(psc, packaging)
        packaging.notification = notification
        explicitlyMockPipelineVariable('quayToken')
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        1 * getPipelineMock("sh")({ it ==~ /^podman build .* ${contextPathString}$/ })
        1 * notification.notifyWithAllMethods(*_)

        where:
        buildContextPathValue << ['./build/path/', '', null]
        contextPathString << [
            '/home/jenkins/myfolder/./build/path/',
            '/home/jenkins/myfolder/',
            '/home/jenkins/myfolder/'
        ]
    }

    def """When buildContext is set, using ecr, the context path is not the default."""() {
        given:
        explicitlyMockPipelineVariable('AWS_PASSWORD')
        explicitlyMockPipelineVariable('AWS_USERNAME')

        def packaging = new PodmanPackaging(
            config: [
                dockerRegistry  : '1234567.dkr.ecr.us-east-1.amazonaws.com',
                cloudName       : cloudName,
                buildContextPath: buildContextPathValue,
                dockerfile      : './mypathto/Dockerfile',
                image           : [
                    name: 'name'
                ],
                ecr             : [
                    credentialsId: 'creds',
                    rolename     : 'test',
                    saml         : true,
                ]
            ],
            script: script,
            psc: psc
        )

        when:
        simulatePodTemplate(psc, packaging, cloudName)
        packaging.notification = notification
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        1 * getPipelineMock("sh")({ it ==~ /^podman build .* ${contextPathString}$/ })
        1 * notification.notifyWithAllMethods(*_)

        where:
        buildContextPathValue << ['./build/path/', '', null]
        contextPathString << [
            '/home/jenkins/myfolder/./build/path/',
            '/home/jenkins/myfolder/',
            '/home/jenkins/myfolder/'
        ]
    }
}
