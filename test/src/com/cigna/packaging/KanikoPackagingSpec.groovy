package com.cigna.packaging

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification

import static com.cigna.common.utils.Utils.calculateContainerName

class KanikoPackagingSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    Notification notification = Mock()

    def setup() {
        explicitlyMockPipelineVariable('steps')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        initScriptAndPsc()
        script.env.GIT_COMMIT = 'abc2dfe9ae321ef8'
    }

    def """When correct properties are set for quay, kaniko auth is configured and kaniko command is run"""() {
        when:
        def packaging = new KanikoPackaging(
                config: [
                        dockerRegistry: 'registry.cigna.com',
                        cloudName     : cloudName,
                        image         : [
                                name       : 'name',
                                org        : 'org',
                                buildArgs  : whereBuildArgs,
                                extraParams: whereExtraParams
                        ],
                        quay          : [
                                credentialsId: 'creds'
                        ]
                ], script: script, psc: psc
        )
        packaging.notification = notification
        simulatePodTemplate(psc, packaging, cloudName)
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        1 * getPipelineMock('sh')({
            it == '#!/busybox/sh -ex\necho "{\\"auths\\":{\\"https://registry.cigna.com/v2\\":{\\"username\\":\\"\\$oauthtoken\\",\\"password\\":\\"Mock Generator for [QUAY_TOKEN]\\"}}}" > /kaniko/.docker/config.json '
        })
        1 * getPipelineMock('sh')({
            it == "/kaniko/executor --context /home/jenkins/myfolder/ --ignore-path /busybox --ignore-path=/usr/bin/newgidmap --ignore-path=/usr/bin/newuidmap --dockerfile /home/jenkins/myfolder/Dockerfile --destination registry.cigna.com/org/name:abc2dfe9${result}"
        })
        1 * notification.notifyWithAllMethods(*_)

        where:
        result << [' --build-arg arg="matey"', ' --ahoy there ', '']
        whereBuildArgs << [[arg: 'matey'], null, null]
        whereExtraParams << [null, '--ahoy there', null]
    }

    def """When conftestValidation property is provided, conftest command is run"""() {
        when:
        def packaging = new KanikoPackaging(
                config: [
                        cloudName         : cloudName,
                        image             : [
                                name: 'name',
                                org : 'org'
                        ],
                        quay              : [
                                credentialsId: 'creds'
                        ],
                        conftestValidation: whereConftest
                ], script: script, psc: psc
        )
        packaging.notification = notification
        simulatePodTemplate(psc, packaging, cloudName)
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        whereConftest * getPipelineMock('sh')({
            it ==~ /conftest test --update.*Dockerfile/
        })
        1 * getPipelineMock('sh')({
            it == '/kaniko/executor --context /home/jenkins/myfolder/ --ignore-path /busybox --ignore-path=/usr/bin/newgidmap --ignore-path=/usr/bin/newuidmap --dockerfile /home/jenkins/myfolder/Dockerfile --destination quay.sys.cigna.com/org/name:abc2dfe9'
        })
        1 * notification.notifyWithAllMethods(*_)

        where:
        whereConftest << [0, 1]
    }

    def """When correct properties are set for ecr, kaniko auth is configured and kaniko command & build args are run for saml2aws"""() {
        setup:
        explicitlyMockPipelineVariable('AWS_PASSWORD')
        explicitlyMockPipelineVariable('AWS_USERNAME')
        when:
        def packaging = new KanikoPackaging(
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
                ], script: script, psc: psc
        )
        packaging.notification = notification
        simulatePodTemplate(psc, packaging, cloudName)
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        1 * getPipelineMock("sh").call('mv /home/jenkins/.ecr/credentials /kaniko/.docker/config.json')
        1 * getPipelineMock("echo").call('Adding ECR secret to Kaniko credentials store:')
        1 * getPipelineMock("echo").call('Using saml2aws...')
        1 * getPipelineMock('sh')({
            it == "/kaniko/executor --context /home/jenkins/myfolder/ --ignore-path /busybox --ignore-path=/usr/bin/newgidmap --ignore-path=/usr/bin/newuidmap --dockerfile /home/jenkins/myfolder/Dockerfile --destination 1234567.dkr.ecr.us-east-1.amazonaws.com/name:abc2dfe9${result}"
        })
        1 * notification.notifyWithAllMethods(*_)

        where:
        result << [' --build-arg arg="matey"', ' --ahoy there ', '']
        whereBuildArgs << [[arg: 'matey'], null, null]
        whereExtraParams << [null, '--ahoy there', null]
    }

    def """When correct properties are set for ecr, kaniko auth is configured and kaniko command is run for saml2aws"""() {
        setup:
        explicitlyMockPipelineVariable('AWS_PASSWORD')
        explicitlyMockPipelineVariable('AWS_USERNAME')
        when:
        def packaging = new KanikoPackaging(
                config: [
                        cloudName     : cloudName,
                        dockerRegistry: '1234567.dkr.ecr.us-east-1.amazonaws.com',
                        image         : [
                                name       : 'name',
                                buildArgs  : whereBuildArgs,
                                extraParams: whereExtraParams
                        ],
                        ecr           : [
                                credentialsId: 'creds',
                                rolename     : 'test',
                                saml         : true
                        ]
                ], script: script, psc: psc
        )
        packaging.notification = notification
        simulatePodTemplate(psc, packaging, cloudName)
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        1 * getPipelineMock("usernamePassword.call").call(['credentialsId': 'creds', 'usernameVariable': 'AWS_USERNAME', 'passwordVariable': 'AWS_PASSWORD'])
        1 * getPipelineMock("withCredentials").call(*_)
        1 * notification.notifyWithAllMethods(*_)

        where:
        result << [' --build-arg arg="world"', ' --hello ', '']
        whereBuildArgs << [[arg: 'world'], null, null]
        whereExtraParams << [null, '--hello', null]
    }

    def """When commit-sha tag is suppressed, the tag is not generated"""() {
        when:
        def packaging = new KanikoPackaging(
                config: [
                        cloudName         : cloudName,
                        suppressCommitTag : supressTag,
                        image             : [
                                name: 'name',
                                org : 'org',
                                tags: [
                                        [
                                                tag   : "test-tag",
                                                expire: false
                                        ]
                                ],
                        ],

                        quay              : [
                                credentialsId: 'creds'
                        ],
                        conftestValidation: false
                ], script: script, psc: psc
        )
        packaging.notification = notification
        simulatePodTemplate(psc, packaging, cloudName)
        packaging.determineImageVars()
        packaging.containerBuildAndPushImage()

        then:
        1 * getPipelineMock('sh')({
            it == "/kaniko/executor --context /home/jenkins/myfolder/ --ignore-path /busybox --ignore-path=/usr/bin/newgidmap --ignore-path=/usr/bin/newuidmap --dockerfile /home/jenkins/myfolder/Dockerfile$tagExpr --destination quay.sys.cigna.com/org/name:test-tag"
        })
        1 * notification.notifyWithAllMethods(*_)

        where:
        supressTag << [true, false]
        tagExpr << ['', ' --destination quay.sys.cigna.com/org/name:abc2dfe9']
    }

    def '''When simple caching boolean is used, ensure correct flags are passed to kaniko'''() {
        when:
        def packaging = new KanikoPackaging(config: [
                cloudName         : cloudName,
                image             : [
                        name: 'name',
                        org : 'org',
                        tags: [
                                [
                                        tag   : "test-tag",
                                        expire: false
                                ]
                        ],
                ],
                cache             : whereCacheFlags,
                quay              : [
                        credentialsId: 'creds'
                ],
                conftestValidation: false
        ], script: script, psc: psc
        )
        packaging.notification = notification
        simulatePodTemplate(psc, packaging, cloudName)

        then:
        assert packaging.constructCacheArguments() == whereExpectedArguments

        where:
        whereCacheFlags << [false, true]
        whereExpectedArguments << [' --cache=false ', ' --cache=true --cache-copy-layers=false --cache-ttl=24h']
    }

    def '''When caching config map is used, ensure correct flags are passed to kaniko'''() {
        when:
        def packaging = new KanikoPackaging(config: [
                cloudName         : cloudName,
                image             : [
                        name: 'name',
                        org : 'org',
                        tags: [
                                [
                                        tag   : "test-tag",
                                        expire: false
                                ]
                        ],
                ],
                cache             : [
                        dir : cacheDir,
                        repo: cacheRepo,
                        ttl : cacheTTL,
                ],
                quay              : [
                        credentialsId: 'creds'
                ],
                conftestValidation: false
        ], script: script, psc: psc
        )
        packaging.notification = notification
        simulatePodTemplate(psc, packaging, cloudName)

        then:
        assert packaging.constructCacheArguments() == whereExpectedArguments

        where:
        cacheDir << ['', '/some/dir']
        cacheRepo << ['', 'https://somerepo.com/repo']
        cacheTTL << ['200h', '96h']
        whereExpectedArguments << [' --cache=true --cache-copy-layers=false --cache-ttl=200h',
                                   ' --cache=true --cache-copy-layers=false --cache-repo=https://somerepo.com/repo --cache-dir=/some/dir --cache-ttl=96h']
    }
    def '''When saml2aws image is overridden using containers'''() {
        given:
        explicitlyMockPipelineStep('setAdditionalPodConfig')
        when:
        KanikoPackaging phase = new KanikoPackaging(
            config:  [
                packagingType: 'kaniko',
                branchPattern  : 'fake',
                dockerRegistry: '535306282211.dkr.ecr.us-east-1.amazonaws.com',
                sdlcEnvironment: 'Dev',
                containers: [
                    [
                        name: 'saml2aws', // only override saml2aws container
                        image: 'enterprise-devops/aws-d-cloudkit:1.2.2'
                    ]
                ],
                image: [
                    name: 'cigna-pipeline',
                    tags: [
                        [
                            tag: 'buildNum',
                            expire: true
                        ]
                    ]
                ],
                ecr: [
                    credentialsId: 'svpOnboardServiceAccount',
                    rolename: 'PIPELINETEST',
                    saml: true,
                ],
                conftestValidation: false
            ],
            script: script, psc: psc
        )
        phase.init()
        phase.prePodConfig()
        phase.postPodConfig()
        then:
        phase.saml2awsContainerName == "aws-d-cloudkitv122" // overridden
        phase.saml2awsImage == 'enterprise-devops/aws-d-cloudkit:1.2.2' // overridden
    }

    def '''When saml2aws image is not overridden'''() {
        given:
        explicitlyMockPipelineStep('setAdditionalPodConfig')
        when:
        KanikoPackaging phase = new KanikoPackaging(
            config:  [
                packagingType: 'kaniko',
                branchPattern  : 'fake',
                dockerRegistry: '535306282211.dkr.ecr.us-east-1.amazonaws.com',
                sdlcEnvironment: 'Dev',
                image: [
                    name: 'cigna-pipeline',
                    tags: [
                        [
                            tag: 'buildNum',
                            expire: true
                        ]
                    ]
                ],
                ecr: [
                    credentialsId: 'svpOnboardServiceAccount',
                    rolename: 'PIPELINETEST',
                    saml: true,
                ],
                conftestValidation: false
            ],
            script: script, psc: psc
        )
        phase.init()
        phase.prePodConfig()
        phase.postPodConfig()
        then:
        phase.saml2awsContainerName == calculateContainerName(KanikoPackaging.SAML2AWS_CONTAINER_IMAGE, KanikoPackaging.SAML2AWS_CONTAINER_VERSION)
        phase.saml2awsImage == "${KanikoPackaging.SAML2AWS_CONTAINER_IMAGE}:${KanikoPackaging.SAML2AWS_CONTAINER_VERSION}"
    }
}
