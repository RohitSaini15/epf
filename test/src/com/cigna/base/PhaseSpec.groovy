package com.cigna.base

import com.cigna.SinglePodTest
import com.cigna.common.exception.InvalidInputException
import com.cigna.modules.Module
import com.cigna.modules.ModuleBridge
import com.evernorth.cloudnativebuild.model.ModuleContract

import java.util.regex.Pattern

class PhaseSpec extends SinglePodTest {
    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('emailext')
        initScriptAndPsc()
    }

    def """usesJunit can return #usesJunit based on presence/absence of junit config opt"""() {
        when:
        def phase = new ValidPhase(
            config: phaseConfig,
            script: script,
            psc: psc
        )
        then:
        assert phase.usesJunit() == usesJunit
        where:
        phaseConfig << [
            [:],
            [junit: [:]],
        ]
        usesJunit << [
            false,
            true,
        ]
    }

    def """When warningsNG config opt is specified, ensure that the usesWarningsNG method correctly executes"""() {
        when:
        def phase = new ValidPhase(
            config: phaseConfig,
            script: script,
            psc: psc
        )
        then:
        assert phase.usesWarningsNG() == usesWarningsNG
        where:
        phaseConfig << [
            [:],
            [warningsNG: [:]],
        ]
        usesWarningsNG << [
            false,
            true,
        ]
    }


    def """When updatAwsFed is called a curl is made"""() {

        when:
        def phase = new ValidPhase(config: [:], script: script,
            psc: psc)
        phase.updateAwsFed('1')

        then:
        1 * getPipelineMock("sh")({ it ==~ /curl -L https:\/\/github.sys.cigna.com\/cigna\/aws-fed\/releases\/download\/v1.*/ })
    }

    def """When awsFed is called a withCredentials step is called AWS_FED env vars are used, and aws-fed
        add and login are called """() {

        explicitlyMockPipelineVariable("AWS_FED_USERNAME")
        explicitlyMockPipelineVariable("AWS_FED_PASSWORD")
        when:
        def awsConfig = [
            credentialsId: 'foo',
            account      : 'testAccount',
            rolename     : 'testRolename',
        ]
        def phase = new ValidPhase(config: [:], script: script,
            psc: psc)
        phase.awsFed(awsConfig)

        then:
        1 * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("sh")({
            it ==~ /(?s)export AWS_FED_PASSWORD=.*export AWS_FED_USERNAME=.*/
        })
        1 * getPipelineMock("sh")("aws-fed add --nickname ci --account testAccount --role testRolename")
        1 * getPipelineMock("sh")('aws-fed login ci')
    }

    def """When awsAssumeRole is called and the proper config is given then a sh script calling the 
            aws sts assume-role command with the given properties is run"""() {
        when:
        def awsConfig = givenConfig
        def phase = new ValidPhase(config: [:], script: script,
            psc: psc)
        phase.prePodConfig()
        phase.awsAssumeRole(awsConfig)

        then:
        assumeRoleSteps * getPipelineMock("sh")({
            it ==~ /(?s).*aws sts assume-role.*arn:aws:iam::test:role\/test.*/
        })

        where:
        givenConfig << [
            [targetAccount: 'test', accountRoleName: 'test'],
            [:]
        ]
        assumeRoleSteps << [1, 0]
    }

    def """When containerImage is provided and updateContainerImage is run then
        the additionalPodConfig has the new containerImage value"""() {
        when:
        def phase = new ValidPhase(script: script,
            psc: psc)
        phase.prePodConfig()
        phase.updateContainerImage(whereContainerImage, 'additionalPodConfig', 0)

        then:
        assert phase.additionalPodConfig.containers[0].image == "${whereContainerImage}:validVersion"
        where:
        whereContainerImage << ["test"]
    }

    def """When containerVersion is provided and updateContainerVersion is run then
        the additionalPodConfig has the new containerVersion value"""() {
        when:
        def phase = new ValidPhase(script: script,
            psc: psc)
        phase.prePodConfig()
        phase.updateContainerVersion(whereContainerVersion, 'additionalPodConfig', 0)

        then:
        assert phase.additionalPodConfig.containers[0].image == "enterprise-devops/validImage:${whereContainerVersion}"
        where:
        whereContainerVersion << ["test"]
    }

    def """When containerMemory is provided and updateContainerMemory is run then
            the additionalPodConfig has the new containerMemory value"""() {
        when:
        def phase = new ValidPhase(script: script,
            psc: psc)
        phase.prePodConfig()
        phase.updateContainerMemory(whereContainerMemoryIn, 'additionalPodConfig', 0)

        then:
        assert phase.additionalPodConfig.containers[0].resources.limits.memory == whereContainerMemoryOut

        where:
        whereContainerMemoryIn << [500, 1000000]
        whereContainerMemoryOut << ["500Mi", "18000Mi"]
    }

    def """When containerCpu is provided and updateContainerCpu is run then
            the additionalPodConfig has the new containerCpu value"""() {
        when:
        def phase = new ValidPhase(script: script,
            psc: psc)
        phase.prePodConfig()
        phase.updateContainerCpu(whereContainerCpuIn, 'additionalPodConfig', 0)

        then:
        assert phase.additionalPodConfig.containers[0].resources.limits.cpu == whereContainerCpuOut

        where:
        whereContainerCpuIn << [500, 6000]
        whereContainerCpuOut << ["500m", "5000m"]
    }

    def """When imagePullPolicy is provided and updateImagePullPolicy is run then
        the additionalPodConfig has the new imagePullPolicy value"""() {
        when:
        def phase = new ValidPhase(script: script,
            psc: psc)
        phase.prePodConfig()
        phase.updateContainerImagePullPolicy(whereImagePullPolicyIn, 'additionalPodConfig', 0)

        then:
        assert phase.additionalPodConfig.containers[0].imagePullPolicy == whereImagePullPolicyOut

        where:
        whereImagePullPolicyIn << [null, 'IfNotPresent']
        whereImagePullPolicyOut << ['Always', 'IfNotPresent']
    }

    def """When a container property is provided and updatePhaseContainer is called then all
        given properties are set in the additionalPodConfig"""() {
        when:
        def phase = new ValidPhase(script: script, config: [:], psc: psc)
        phase.prePodConfig()
        phase.updatePhaseContainer(whereContainerConfig)

        then:
        assert phase.additionalPodConfig.containers[0].image == "$whereContainerImage:$whereContainerVersion"
        assert phase.additionalPodConfig.containers[0].resources.limits.cpu == whereContainerCpu
        assert phase.additionalPodConfig.containers[0].resources.limits.memory == whereContainerMemory
        assert phase.additionalPodConfig.containers[0].imagePullPolicy == whereImagePullPolicy

        where:
        whereContainerConfig << [null, [image: 'enterprise-devops/test', version: 'test', cpu: 250, memory: 250, imagePullPolicy: 'Never']]
        whereContainerImage << ['enterprise-devops/validImage', 'enterprise-devops/test']
        whereContainerVersion << ['validVersion', 'test']
        whereContainerCpu << ['25m', '250m']
        whereContainerMemory << ['25Mi', '250Mi']
        whereImagePullPolicy << ['Always', 'Never']
    }

    def """An exception is thrown when an illegal image pull policy is specified."""() {
        when:
        def phase = new ValidPhase(script: script,
            psc: psc)
        phase.config = [
            container: [image: 'enterprise-devops/test', version: 'test', cpu: 250, memory: 250, imagePullPolicy: 'Sometimes']
        ]
        phase.prePodConfig()

        then:
        InvalidInputException ex = thrown()
        ex.message == "The imagePullPolicy 'Sometimes' is not a valid policy [Always, IfNotPresent, Never]"
    }

    def '''configuring images using industry conventions parses the version correctly'''() {
        given:
        def phase = new ValidPhase(script: script,
            psc: psc)
        when:
        phase.config = [
            containers: [
                [
                    name : 'validPhase',
                    image: 'nginx:1.0'
                ],
                [
                    name   : 'secondPhase',
                    image  : 'nginx',
                    version: '2.0',
                ]

            ]
        ]
        phase.prePodConfig()
        then:
        phase.additionalPodConfig.containers[0].name == 'nginxv10'
        phase.additionalPodConfig.containers[0].image == 'nginx:1.0'
        phase.additionalPodConfig.containers[1].name == 'nginxv20'
        phase.additionalPodConfig.containers[1].image == 'nginx:2.0'
    }

    def """When a list of containers is provided and prePodConfig is called then all
        given properties are set in the appropriate pod config"""() {
        when:
        def phase = new ValidPhase(
            config: [
                containers: [
                    [
                        name           : 'sonar',
                        image          : 'sonar',
                        version        : 'test',
                        imagePullPolicy: 'Always',
                        cpu            : 2500,
                        memory         : 4000
                    ],
                    [
                        name           : 'checkmarx',
                        image          : 'checkmarx',
                        version        : 'test',
                        imagePullPolicy: 'IfNotPresent',
                        cpu            : 1000,
                        memory         : 5000
                    ],
                    [
                        image          : 'anotherimage',
                        version        : 'test',
                        imagePullPolicy: 'Never',
                        cpu            : 1000,
                        memory         : 1000
                    ]
                ]
            ],
            script: script,
            psc: psc
        )
        phase.prePodConfig()

        then:
        assert phase.basePodConfig.containers.any { container ->
            container.name == 'sonarvtest' &&
                container.image == 'sonar:test' &&
                container.imagePullPolicy == 'Always' &&
                container.resources.limits.cpu == '2500m' &&
                container.resources.limits.memory == '4000Mi'
        }
        assert phase.basePodConfig.containers.any { container ->
            container.name == 'checkmarxvtest' &&
                container.image == 'checkmarx:test' &&
                container.imagePullPolicy == 'IfNotPresent' &&
                container.resources.limits.cpu == '1000m' &&
                container.resources.limits.memory == '5000Mi'
        }
        assert phase.additionalPodConfig.containers.any { container ->
            container.name == 'anotherimagevtest' &&
                container.image == 'anotherimage:test' &&
                container.imagePullPolicy == 'Never' &&
                container.resources.limits.cpu == '1000m' &&
                container.resources.limits.memory == '1000Mi'
        }
    }

    def """customize stage name"""() {
        given:
        script.env.JENKINS_URL = "https://orchestrator1.orchestrator-v2.sys.cigna.com"
        script.env.JOB_NAME = 'orchestrators-folders/adjudicator/job/Adjudicator/job/main/job/10'
        def module = new Module(script: script, config: [
            moduleType   : 'initialize',
            moduleName   : 'cnp-image-validation',
            branchPattern: 'release-branch',
            cloudName    : 'cloudName',
            stageName    : 'custom stage name'
        ],
            psc: psc)

        module.bridge = new ModuleBridge(script)
        module.bridge.moduleContract = new ModuleContract(
            moduleName: 'cnp-build-publish-maven',
            contractName: 'BUILD',
            commandName: "cnp-build-publish-maven-create.sh",
            subCommand: "build",
            image: 'someimage',
            requiresUnStash: false
        )

        when:
        simulatePodTemplate(psc, module)
        module.run()

        then:
        1 * getPipelineMock('stage')(_) >> { _arguments ->
            assert _arguments[0][0] == 'custom stage name'
        }
    }

    def """default stage name"""() {
        given:
        def module = new Module(script: script,
            psc: psc)
        script.env.JENKINS_URL = "https://orchestrator1.orchestrator-v2.sys.cigna.com"
        script.env.JOB_NAME = 'orchestrators-folders/adjudicator/job/Adjudicator/job/main/job/10'
        module.config = [
            moduleType   : 'initialize',
            moduleName   : 'cnp-image-validation',
            branchPattern: 'release-branch',
            cloudName    : 'cloudName'
        ]
        module.bridge = new ModuleBridge(script)
        module.bridge.moduleContract = new ModuleContract(
            moduleName: 'cnp-build-publish-maven',
            contractName: 'BUILD',
            commandName: "cnp-build-publish-maven-create.sh",
            subCommand: "build",
            image: 'someimage',
            requiresUnStash: false
        )

        when:
        simulatePodTemplate(psc, module)
        module.run()

        then:
        1 * getPipelineMock('stage')(*_) >> { _arguments ->
            assert _arguments[0][0] == 'initialize: INIT'
        }
    }

    def "withPhaseConfigEnv calls withEnv iff there is a non-empty env list to pass using phase config #phaseConfig"() {
        given:
        def subjectPhase = new ValidPhase(config: phaseConfig,
            script: script,
            psc: psc)
        def fillerClosure = {}
        when:
        subjectPhase.withPhaseConfigEnv(arg, fillerClosure)
        then:
        callCount * getPipelineMock('withEnv')(expectedEnv, *_)
        where:
        phaseConfig << [[withEnv: 'configVar=value'], [withEnv: 'configVar=value'], [withEnv: 'configVar=value'], [:], [:], [:]]
        arg << [['argVar=value'], [], null, ['argVar=value'], [], null]
        callCount << [1, 1, 1, 1, 0, 0]
        expectedEnv << [
            ['argVar=value', 'configVar=value'],
            ['configVar=value'],
            ['configVar=value'],
            ['argVar=value'],
            *_,
            *_,
        ]
    }

    def """send custom email with/without attachmentFile when emailRecipients is truthy"""() {
        when:
        def phase = new ValidPhase(config: [email: configEmail], script: script)
        phase.sendTailoredEmail()

        then:
        callCount * getPipelineMock('emailext')(expectedEmail)
        where:
        callCount << [1, 0]
        configEmail << [[
                            recipients    : 'person@cigna.com',
                            subject       : 'EPF pipeline execution',
                            attachmentFile: 'index.html',
                            body          : 'Hi, EPF pipeline execution details']

                        , [:]]
        expectedEmail << [[
                              to                : 'person@cigna.com',
                              subject           : 'EPF pipeline execution',
                              attachmentsPattern: 'index.html',
                              body              : 'Hi, EPF pipeline execution details<br><br>Build URL: some build url',
                              mimeType          : 'text/html'],
                          *_]
    }

    def '''ensure that the email zip configuration correctly handles canonical file paths using email config #config'''() {
        given:
        script.env.WORKSPACE = './'
        when:
        def phase = new ValidPhase(config: config, script: script)
        phase.sendTailoredEmail()
        then:
        1 * getPipelineMock('emailext')(expectedEmailCall)
        expectedCalls * getPipelineMock('zip').call({ Map zipParams ->
            expectedZipCall.every { key, expectedValue ->
                def actualValue = zipParams[key]
                if (expectedValue instanceof Pattern) {
                    return actualValue ==~ expectedValue
                } else {
                    return actualValue == expectedValue
                }
            }
        })
        where:
        config << [
            [
                email: [
                    recipients: 'first.last@evernorth.com',
                    body      : 'EPF JaCoCo Report',
                    subject   : 'EPF JaCoCo Report',
                    zip       : [folder: 'target', name: 'reports.zip']
                ]
            ],
            [
                email: [
                    recipients: 'first.last@evernorth.com',
                    body      : 'EPF JaCoCo Report',
                    subject   : 'EPF JaCoCo Report',
                    zip       : [
                        folder   : 'target',
                        name     : 'reports.zip',
                        includes : 'anything',
                        excludes : 'nothing',
                        archive  : true,
                        overwrite: false
                    ]
                ]
            ],
            [
                email: [
                    recipients    : 'first.last@evernorth.com',
                    body          : 'EPF JaCoCo Report',
                    subject       : 'EPF JaCoCo Report',
                    attachmentFile: '**/target/*'
                ]
            ],
        ]
        expectedZipCall << [
            [
                zipFile        : 'reports.zip',
                dir            : Pattern.compile(/.*target/),
                defaultIncludes: '**/*',
                defaultExcludes: '',
                archive        : false,
                overwrite      : true,
            ],
            [
                zipFile        : 'reports.zip',
                dir            : Pattern.compile(/.*target/),
                defaultIncludes: 'anything',
                defaultExcludes: 'nothing',
                archive        : true,
                overwrite      : false,
            ], [:]
        ]
        expectedEmailCall << [
            [
                to                : 'first.last@evernorth.com',
                subject           : 'EPF JaCoCo Report',
                attachmentsPattern: '**/reports.zip',
                body              : 'EPF JaCoCo Report<br><br>Build URL: some build url',
                mimeType          : 'text/html'
            ],
            [
                to                : 'first.last@evernorth.com',
                subject           : 'EPF JaCoCo Report',
                attachmentsPattern: '**/reports.zip',
                body              : 'EPF JaCoCo Report<br><br>Build URL: some build url',
                mimeType          : 'text/html'
            ],
            [
                to                : 'first.last@evernorth.com',
                subject           : 'EPF JaCoCo Report',
                attachmentsPattern: '**/target/*',
                body              : 'EPF JaCoCo Report<br><br>Build URL: some build url',
                mimeType          : 'text/html'
            ]
        ]
        expectedCalls << [1, 1, 0]

    }

    def '''verify branchPattern is honored during email processing'''() {
        when:
        script.scm.branches[0].name = 'release/1.0.0'
        def phase = new ValidPhase(config: [email: configEmail], script: script)
        phase.sendTailoredEmail()

        then:
        callCount * getPipelineMock('emailext')(expectedEmail)
        where:
        callCount << [1, 1, 0]
        configEmail << [
            [
                recipients    : 'person@cigna.com',
                branchPattern : 'release/1.0.0',
                subject       : 'EPF pipeline execution',
                attachmentFile: 'index.html',
                body          : 'Hi, EPF pipeline execution details'
            ],
            [
                recipients    : 'person@cigna.com',
                subject       : 'EPF pipeline execution',
                attachmentFile: 'index.html',
                body          : 'Hi, EPF pipeline execution details'
            ],
            [
                recipients    : 'person@cigna.com',
                branchPattern : 'wrong/1.0.0',
                subject       : 'EPF pipeline execution',
                attachmentFile: 'index.html',
                body          : 'Hi, EPF pipeline execution details'
            ]
        ]
        expectedEmail << [
            [
                to                : 'person@cigna.com',
                subject           : 'EPF pipeline execution',
                attachmentsPattern: 'index.html',
                body              : 'Hi, EPF pipeline execution details<br><br>Build URL: some build url',
                mimeType          : 'text/html'
            ],
            [
                to                : 'person@cigna.com',
                subject           : 'EPF pipeline execution',
                attachmentsPattern: 'index.html',
                body              : 'Hi, EPF pipeline execution details<br><br>Build URL: some build url',
                mimeType          : 'text/html'
            ],
            *_
        ]
    }
}