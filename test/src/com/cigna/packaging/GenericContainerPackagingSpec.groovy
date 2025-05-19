package com.cigna.packaging


import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class GenericContainerPackagingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
            GIT_BRANCH                    : 'stuff',
            GIT_COMMIT                    : 'some-commit-sha',
            GIT_PREVIOUS_COMMIT           : 'stuff',
            GIT_PREVIOUS_SUCCESSFUL_COMMIT: 'stuff',
            WORKSPACE                     : 'workspace_dir'
        ]
    }
    def script = new Script()

    class GenericContainerPackagingClass extends GenericContainerPackaging {

        @Override
        void packageApplication() {}

        @Override
        void containerBuildAndPushImage() {}

    }

    def setup() {
        explicitlyMockPipelineVariable('steps')
    }

    def """When validate is called and inappropriate config is provided issues are added"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [packagingType: 'kaniko', branchPattern: 'test'] + whereConfig,
            script: script
        )
        def issues = packaging.validate()

        then:
        assert issues.size() == whereIssues

        where:
        whereIssues << [2, 2, 1, 0, 2, 1, 0]
        whereConfig << [
            [:],
            [quay: [credentialsId: 'test']],
            [quay: [credentialsId: 'test'], image: [name: 'test']],
            [quay: [credentialsId: 'test'], image: [name: 'test', org: 'test']],
            [ecr: [credentialsId: 'test']],
            [ecr: [credentialsId: 'test', rolename: 'test']],
            [ecr: [credentialsId: 'test', rolename: 'test'], image: [name: 'test']]
        ]
    }

    def """When multiple tags are defined, and determineImageVars is called
        they are appended to both the tag list and urlList"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name: 'name',
                    org : 'org',
                    tags: [
                        [tag: 'One', expire: true],
                        [tag: 'Two', expire: false],
                        [tag: 'Three', expire: true]
                    ],
                ],
                quay : 'notNull'
            ],
            script: script
        )
        packaging.determineImageVars()

        then:
        1 * getPipelineMock('echo')({ it ==~ /Building tag: One Expire: true/ })
        1 * getPipelineMock('echo')({ it ==~ /Building tag: Two Expire: false/ })
        1 * getPipelineMock('echo')({ it ==~ /Building tag: Three Expire: true/ })
        assert packaging.imageName == 'org/name'
        assert packaging.imageTag == 'some-com'
        assert packaging.imageTagMapList == [
            [tag: 'some-com', expire: true],
            [tag: 'One', expire: true],
            [tag: 'Two', expire: false],
            [tag: 'Three', expire: true],
        ]
        assert packaging.imageUrls == [
            'quay.sys.cigna.com/org/name:some-com',
            'quay.sys.cigna.com/org/name:One',
            'quay.sys.cigna.com/org/name:Two',
            'quay.sys.cigna.com/org/name:Three'
        ]
    }

    def """When a dockerfile path is provided, the build command uses the --file arg"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name      : 'name',
                    org       : 'org',
                    dockerfile: 'Dockerfile.example',
                    buildArgs : [
                        key: 'value'
                    ]
                ]
            ],
            script: script
        )
        String argString = packaging.constructBuildArgsString()

        then:
        argString ==~ "--build-arg key=\"value\" "
    }

    def """When a dockerfile path is not provided, the build command does not use the --file arg"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name     : 'name',
                    org      : 'org',
                    buildArgs: [
                        key: 'value'
                    ]
                ]
            ],
            script: script
        )
        String argString = packaging.constructBuildArgsString()

        then:
        argString ==~ "--build-arg key=\"value\" "
    }

    def """When quayScanImage method is called, an echo is run, and the build and push script is run
            with Quay Credentials"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name     : 'name',
                    org      : 'org',
                    runScan  : true,
                    buildArgs: [
                        key1: 'value1',
                        key2: 'value2'
                    ]
                ],
                quay : [
                    apiTokenCredentialsId: 'token-id'
                ]
            ],
            script: script
        )
        explicitlyMockPipelineVariable('quayToken')
        packaging.imageName = 'org/image'
        packaging.imageTag = 'tag'
        packaging.imageTagMapList = [[tag: 'tag', expire: 'true']]
        packaging.quayScanImage()

        then:
        1 * getPipelineMock('echo')({ it ==~ /.*Quay Scanner no longer generates accurate vulnerability reports, and is not supported anymore.*/ })
    }

    def """When quayExpireImage method is called, an echo is run, and the build and push script is
            run with Quay Credentials"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name     : 'name',
                    org      : 'org',
                    buildArgs: [
                        key1: 'value1',
                        key2: 'value2'
                    ],
                    tags     : [
                        [
                            tag       : 'tag',
                            expire    : true,
                            expiration: '3w'
                        ]
                    ]
                ],
                quay : [
                    apiTokenCredentialsId: 'token-id'
                ]
            ],
            script: script
        )
        explicitlyMockPipelineVariable('quayToken')
        packaging.determineImageVars()
        packaging.quayExpireImage()

        then:
        1 * getPipelineMock('echo')({ it ==~ /Setting image to expire in 3w/ })
        1 * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("sh")({ it ==~ /quay repository expire "quay.sys.cigna.com\/org\/name:tag" --host "https:\/\/quay.sys.cigna.com" --expiration 3w --token .*/ })
    }

    def "When quaySetVisibility method is called, an echo is run, and the build and push script is run with Quay Credentials"() {
        when:
        def packaging = new GenericContainerPackagingClass(
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
                    apiTokenCredentialsId: 'token-id'
                ]
            ],
            script: script
        )
        explicitlyMockPipelineVariable('quayToken')
        packaging.imageName = 'org/image'
        packaging.imageTag = 'tag'
        packaging.quaySetVisibility()

        then:
        1 * getPipelineMock('echo')({ it ==~ /Setting image visibility to public/ })
        1 * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("sh").call('quay repository visibility "org/image" --host "https://quay.sys.cigna.com" public --token "Mock Generator for [quayToken]" ')
    }

    def """Custom Docker Registry"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                dockerRegistry: 'registry-dev.cigna.com',
                image         : [
                    name: 'name',
                    org : 'org',
                    tags: [
                        [tag: 'One', expire: true],
                        [tag: 'Two', expire: false],
                        [tag: 'Three', expire: true]
                    ],
                ],
                quay          : [
                    credentialsId: 'test'
                ]
            ],
            script: script
        )
        packaging.determineImageVars()

        then:
        1 * getPipelineMock('echo')({ it ==~ /Building tag: One Expire: true/ })
        1 * getPipelineMock('echo')({ it ==~ /Building tag: Two Expire: false/ })
        1 * getPipelineMock('echo')({ it ==~ /Building tag: Three Expire: true/ })
        assert packaging.imageName == 'org/name'
        assert packaging.imageTag == 'some-com'
        assert packaging.imageTagMapList == [
            [tag: 'some-com', expire: true],
            [tag: 'One', expire: true],
            [tag: 'Two', expire: false],
            [tag: 'Three', expire: true],
        ]
        assert packaging.imageUrls == [
            'registry-dev.cigna.com/org/name:some-com',
            'registry-dev.cigna.com/org/name:One',
            'registry-dev.cigna.com/org/name:Two',
            'registry-dev.cigna.com/org/name:Three'
        ]
    }

    def """When setting override parameter, override existing tag when expire is true"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                dockerRegistry: 'registry-dev.cigna.com',
                image         : [
                    name: 'name',
                    org : 'org',
                    tags: [
                        [
                            tag     : 'One',
                            expire  : true,
                            override: 'never'
                        ],
                    ],
                ],
                quay          : [
                    credentialsId: 'test'
                ]
            ],
            script: script
        )
        packaging.quayExpireImage()

        then:
        1 * getPipelineMock('withCredentials').call(*_)
        1 * getPipelineMock("string.call").call(['credentialsId': 'test', 'variable': 'quayToken'])
    }

    def """When quayExpireImage method is called with a single tag set but no expiration, an warning is generated
        that image will not expire"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name: 'name',
                    org : 'org',
                    tags: [
                        [tag: 'single',]
                    ]
                ],
                quay : [
                    apiTokenCredentialsId: 'token-id'
                ]
            ],
            script: script
        )
        explicitlyMockPipelineVariable('quayToken')
        packaging.determineImageVars()
        packaging.quayExpireImage()

        then:
        4 * getPipelineMock("echo").call(*_)
        1 * getPipelineMock("string.call").call(*_)
        2 * getPipelineMock("sh").call(*_)
    }

    def """When quayExpireImage method is called with only default tag set and  expiration provided, the expiration
        is used in expire command"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name: 'name',
                    org : 'org',
                ],
                quay : [
                    apiTokenCredentialsId: 'token-id',
                    defaultExpiration    : '2w'
                ]
            ],
            script: script
        )
        explicitlyMockPipelineVariable('quayToken')
        packaging.determineImageVars()
        packaging.quayExpireImage()

        then:
        2 * getPipelineMock("echo").call(*_)
        1 * getPipelineMock("sh").call(*_)
        1 * getPipelineMock("string.call").call(*_)
    }

    def """When quayExpireImage method is called with multiple tags set but only defaultExpiration is defined, the
        the default expiration value is set for both invocations"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name: 'name',
                    org : 'org',
                    tags: [
                        [tag: 'One', expire: true],
                    ],
                ],
                quay : [
                    apiTokenCredentialsId: 'token-id',
                    defaultExpiration    : '2w'
                ]
            ],
            script: script
        )
        explicitlyMockPipelineVariable('quayToken')
        packaging.determineImageVars()
        packaging.quayExpireImage()

        then:
        4 * getPipelineMock("echo").call(*_)
        2 * getPipelineMock("sh").call(*_)
        1 * getPipelineMock("withCredentials").call(*_)
    }

    def """When quayExpireImage method is called with multiple tags set with different expirations is defined, the
        different expirations are respected"""() {
        when:
        def packaging = new GenericContainerPackagingClass(
            config: [
                image: [
                    name: 'name',
                    org : 'org',
                    tags: [
                        [tag: 'One', expire: true, expiration: '4w'],
                    ],
                ],
                quay : [
                    apiTokenCredentialsId: 'token-id',
                    defaultExpiration    : '2w'
                ]
            ],
            script: script
        )
        explicitlyMockPipelineVariable('quayToken')
        packaging.determineImageVars()
        packaging.quayExpireImage()

        then:
        4 * getPipelineMock("echo").call(*_)
        1 * getPipelineMock("string.call").call(*_)
    }
}