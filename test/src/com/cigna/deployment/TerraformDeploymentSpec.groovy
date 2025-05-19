package com.cigna.deployment

import com.cigna.SinglePodTest

class TerraformDeploymentSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """When validate is called and required configuration items are missing, issues are raised """() {

        when:
        def terraformDeployment = new TerraformDeployment(
                script: script, psc: psc,
        )

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                awsFed         : [
                        credentialsId: awsFedCredentialsId,
                        account      : awsFedAccount,
                        rolename     : awsFedRolename
                ],
                directories    : [
                        [
                                directory: directory
                        ]
                ]
        ]
        def issues = terraformDeployment.validate()

        then:
        issues.size() == numberOfIssues

        where:
        awsFedCredentialsId << [null, 'test', 'test', 'test']
        awsFedAccount << ['test', null, 'test', 'test']
        awsFedRolename << ['test', 'test', null, 'test']
        directory << ['test', 'test', 'test', null]
        numberOfIssues << [1, 1, 1, 1]
    }

    def """When validate is called and runInAWS is called, issues presented accordingly"""() {
        when:
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)

        terraformDeployment.config = [
                runInAWS       : whereRunInAWS,
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                directories    : [
                        [
                                directory: 'test'
                        ]
                ]
        ]
        terraformDeployment.config += whereConfig
        def issues = terraformDeployment.validate()

        then:
        issues.size() == numberOfIssues

        where:
        whereConfig << [
                [awsFed: [credentialsId: 'test', account: 'test', rolename: 'test']],
                [:],
                [awsFed: [credentialsId: 'test', account: 'test', rolename: 'test']],
                [aws: [targetAccount: null, accountRoleName: null]],
                [aws: [targetAccount: null, accountRoleName: 'test']],
                [aws: [targetAccount: 'test', accountRoleName: null]],
                [aws: [targetAccount: 'test', accountRoleName: 'test']]
        ]
        whereRunInAWS << [null, true, true, true, true, true, true]
        numberOfIssues << [0, 0, 1, 0, 0, 0, 0]
    }

    def """When an invalid terraform.logLevel is given an error is raised otherwise no error raised"""() {

        when:
        def terraformDeployment = new TerraformDeployment(
                script: script, psc: psc,
        )

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                terraform      : [
                        logLevel: logLevel
                ],
                directories    : [
                        [
                                directory: './'
                        ]
                ]
        ]

        def issues = terraformDeployment.validate()

        then:
        issues.size() == numberOfIssues

        where:
        logLevel << ['trace', 'debug', 'info', 'warn', 'error', null, 'superInvalid']
        numberOfIssues << [0, 0, 0, 0, 0, 0, 1]
    }

    def """When terragrunt is called without useAll init, plan, and apply are called, with useAll
        init, plan-all, and apply-all are called. Also the extraArgs are applied"""() {

        when:
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                terragrunt     : [
                        version: version
                ],
                directories    : [
                        [
                                directory: 'directory',
                                extraArgs: [
                                        init : 'initExtra',
                                        plan : 'planExtra',
                                        apply: 'applyExtra'
                                ],
                                useAll   : useAll
                        ]
                ]
        ]
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.terragrunt()

        then:
        if (!useAll) {
            1 * getPipelineMock("sh")(
                    'terragrunt init initExtra --terragrunt-non-interactive --terragrunt-include-external-dependencies'
            )
            1 * getPipelineMock("sh")(
                    'terragrunt plan planExtra --terragrunt-non-interactive '
                            + '--terragrunt-include-external-dependencies -out=tfplan'
            )
            1 * getPipelineMock("sh")(
                    'terragrunt apply applyExtra --terragrunt-non-interactive '
                            + '--terragrunt-include-external-dependencies -auto-approve tfplan'
            )
        } else if (version == '0.27.1') {
            1 * getPipelineMock("sh")(
                    'terragrunt init-all initExtra --terragrunt-non-interactive'
                            + ' --terragrunt-include-external-dependencies --terragrunt-parallelism 4'
            )
            1 * getPipelineMock("sh")(
                    'terragrunt plan-all planExtra --terragrunt-non-interactive '
                            + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4'
            )
            1 * getPipelineMock("sh")(
                    'terragrunt apply-all applyExtra --terragrunt-non-interactive '
                            + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4 -auto-approve'
            )
        } else {
            1 * getPipelineMock("sh")(
                    'terragrunt run-all init initExtra --terragrunt-non-interactive'
                            + ' --terragrunt-include-external-dependencies --terragrunt-parallelism 4'
            )
            1 * getPipelineMock("sh")(
                    'terragrunt run-all plan planExtra --terragrunt-non-interactive '
                            + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4'
            )
            1 * getPipelineMock("sh")(
                    'terragrunt run-all apply applyExtra --terragrunt-non-interactive '
                            + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4 -auto-approve'
            )
        }

        where:
        version << ['0.27.1', '0.27.1', '0.29.0', '0.29.0']
        useAll << [false, true, false, true]
    }

    def """When terragrunt is called with tvars they are added to the command"""() {

        when:
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                terragrunt     : [
                        version: '0.31.6'
                ],
                directories    : [
                        [
                                directory: 'directory',
                                extraArgs: [
                                        init : 'initExtra',
                                        plan : 'planExtra',
                                        apply: 'applyExtra'
                                ],
                                tfVars   : [
                                        env           : 'dev',
                                        account_number: '12345',
                                ],
                                useAll   : true
                        ]
                ]
        ]
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.terragrunt()

        then:
        1 * getPipelineMock("sh")(
                'TF_VAR_env=dev TF_VAR_account_number=12345 terragrunt run-all init initExtra'
                        + ' --terragrunt-non-interactive --terragrunt-include-external-dependencies --terragrunt-parallelism 4'
        )
        1 * getPipelineMock("sh")(
                'TF_VAR_env=dev TF_VAR_account_number=12345 terragrunt run-all plan planExtra --terragrunt-non-interactive '
                        + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4'
        )
        1 * getPipelineMock("sh")(
                'TF_VAR_env=dev TF_VAR_account_number=12345 terragrunt run-all apply applyExtra --terragrunt-non-interactive '
                        + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4 -auto-approve'
        )
    }

    def """When terragrunt is called with planOnly the apply command is not run"""() {

        when:
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                terragrunt     : [
                        version: '0.31.6'
                ],
                directories    : [
                        [
                                directory: 'directory',
                                extraArgs: [
                                        init : 'initExtra',
                                        plan : 'planExtra',
                                        apply: 'applyExtra'
                                ],
                                planOnly : true,
                                useAll   : true
                        ]
                ]
        ]
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.terragrunt()

        then:
        1 * getPipelineMock("sh")(
                'terragrunt run-all init initExtra --terragrunt-non-interactive'
                        + ' --terragrunt-include-external-dependencies --terragrunt-parallelism 4'
        )
        1 * getPipelineMock("sh")(
                'terragrunt run-all plan planExtra --terragrunt-non-interactive '
                        + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4'
        )
        1 * getPipelineMock("echo")('Plan only set to true - skipping apply')
    }

    def """When terragrunt is called with destroy the destroy command is only run"""() {

        when:
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                terragrunt     : [
                        version: '0.31.6'
                ],
                directories    : [
                        [
                                directory: 'directory',
                                extraArgs: [
                                        init   : 'initExtra',
                                        destroy: 'destroyExtra'
                                ],
                                destroy  : true,
                                useAll   : true
                        ]
                ]
        ]
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.terragrunt()

        then:
        1 * getPipelineMock("sh")(
                'terragrunt run-all init initExtra --terragrunt-non-interactive'
                        + ' --terragrunt-include-external-dependencies --terragrunt-parallelism 4'
        )
        1 * getPipelineMock("sh")(
                'terragrunt run-all destroy destroyExtra --terragrunt-non-interactive '
                        + '--terragrunt-include-external-dependencies --terragrunt-parallelism 4 -auto-approve'
        )
    }

    def """When deploy is called, if terraform.version is defined tfswitch is called, if
        terragrunt.version or awsFed.version is called a curl is made. If
        awsFed is defined awsFed method is called, and terragrunt method is always called along with 
        TF_LOG environment variable set"""() {

        when:
        explicitlyMockPipelineVariable("AWS_FED_USERNAME")
        explicitlyMockPipelineVariable("AWS_FED_PASSWORD")
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                terraform      : [
                        version: terraformVersion,
                        logLvel: logLevel,
                ],
                terragrunt     : [
                        version: terragruntVersion
                ],
                awsFed         : awsFed,
                directories    : [
                        [
                                directory: 'directory',
                        ]
                ]
        ]
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.deploy()

        then:
        timesCalled * getPipelineMock("sh")({ it ==~ /aws-fed add .*/ })
        timesCalled * getPipelineMock("sh")('tfswitch 1.0.5')
        1 * getPipelineMock("withEnv")(*_)

        where:
        terraformVersion << [null, '1.0.5']
        terragruntVersion << [null, '0.27.0']
        awsFed << [null, [version: '1', credentialsId: 'a', account: 'a', rolename: 'a']]
        logLevel << [null, 'trace']
        timesCalled << [0, 1]
    }

    def """When deploy is called, if aws.saml is true then call saml2aws"""() {

        when:
        explicitlyMockPipelineVariable("AWS_USERNAME")
        explicitlyMockPipelineVariable("AWS_PASSWORD")
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)

        terraformDeployment.config = [
                deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                terraform      : [
                        version: 'version',
                        logLvel: logLevel,
                ],
                terragrunt     : [
                        version: 'version'
                ],
                aws            : [
                        saml         : true,
                        credentialsId: 'a',
                        account      : 'a',
                        rolename     : 'a',
                ],
                directories    : [
                        [
                                directory: 'directory',
                        ]
                ]
        ]
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.deploy()

        then:
        6 * getPipelineMock("sh").call(*_)
        1 * getPipelineMock("withEnv")(*_)

        where:
        logLevel << [null, 'trace']
    }

    def "When terragruntInitCommand is called"() {
        expect:
        TerraformDeployment.getTerragruntInitCommand(tfVars, version, runAll, extraArgs, terragruntExtraArgs) == initCommand
        where:
        tfVars << ["TF_VAR_env=dev ", "", "TF_VAR_env=dev TF_VAR_account_number=2 ", '']
        version << ["0.28.1", "0.27.1", "0.28.1", '0.28.1']
        runAll << [true, true, true, false]
        extraArgs << ['', '--terragrunt1', '--terragrunt1', '']
        terragruntExtraArgs << ['', '--terragrunt2', '', '']
        initCommand << [
                'TF_VAR_env=dev terragrunt run-all init --terragrunt-parallelism 4',
                'terragrunt init-all --terragrunt1 --terragrunt2 --terragrunt-parallelism 4',
                'TF_VAR_env=dev TF_VAR_account_number=2 terragrunt run-all init --terragrunt1 --terragrunt-parallelism 4',
                'terragrunt init'
        ]
    }

    def "When terragruntPlanCommand is called"() {
        expect:
        TerraformDeployment.getTerragruntPlanCommand(tfVars, version, runAll, extraArgs, terragruntExtraArgs) == planCommand
        where:
        tfVars << ["TF_VAR_env=dev ", "", "TF_VAR_env=dev TF_VAR_account_number=2 ", '']
        version << ["0.28.1", "0.27.1", "0.28.1", '0.28.1']
        runAll << [true, true, true, false]
        extraArgs << ['', '--terragrunt1', '--terragrunt1', '']
        terragruntExtraArgs << ['', '--terragrunt2', '', '']
        planCommand << [
                'TF_VAR_env=dev terragrunt run-all plan --terragrunt-parallelism 4',
                'terragrunt plan-all --terragrunt1 --terragrunt2 --terragrunt-parallelism 4',
                'TF_VAR_env=dev TF_VAR_account_number=2 terragrunt run-all plan --terragrunt1 --terragrunt-parallelism 4',
                'terragrunt plan -out=tfplan'
        ]
    }

    def "When terragruntApplyCommand is called"() {
        expect:
        TerraformDeployment.getTerragruntApplyCommand(tfVars, version, runAll, extraArgs, terragruntExtraArgs) == applyCommand
        where:
        tfVars << ["TF_VAR_env=dev ", "", "TF_VAR_env=dev TF_VAR_account_number=2 ", '']
        version << ["0.28.1", "0.27.1", "0.28.1", '0.28.1']
        runAll << [true, true, true, false]
        extraArgs << ['', '--terragrunt1', '--terragrunt1', '']
        terragruntExtraArgs << ['', '--terragrunt2', '', '']
        applyCommand << [
                'TF_VAR_env=dev terragrunt run-all apply --terragrunt-parallelism 4 -auto-approve',
                'terragrunt apply-all --terragrunt1 --terragrunt2 --terragrunt-parallelism 4 -auto-approve',
                'TF_VAR_env=dev TF_VAR_account_number=2 terragrunt run-all apply --terragrunt1 --terragrunt-parallelism 4 -auto-approve',
                'terragrunt apply -auto-approve tfplan'
        ]
    }

    def "When terragruntDestroyCommand is called"() {
        expect:
        TerraformDeployment.getTerragruntDestroyCommand(tfVars, version, runAll, extraArgs, terragruntExtraArgs) == destroyCommand
        where:
        tfVars << ["TF_VAR_env=dev ", "", "TF_VAR_env=dev TF_VAR_account_number=2 ", '']
        version << ["0.28.1", "0.27.1", "0.28.1", '0.28.1']
        runAll << [true, true, true, false]
        extraArgs << ['', '--terragrunt1', '--terragrunt1', '']
        terragruntExtraArgs << ['', '--terragrunt2', '', '']
        destroyCommand << [
                'TF_VAR_env=dev terragrunt run-all destroy --terragrunt-parallelism 4 -auto-approve',
                'terragrunt destroy-all --terragrunt1 --terragrunt2 --terragrunt-parallelism 4 -auto-approve',
                'TF_VAR_env=dev TF_VAR_account_number=2 terragrunt run-all destroy --terragrunt1 --terragrunt-parallelism 4 -auto-approve',
                'terragrunt destroy -auto-approve'
        ]
    }

    def "When deploy is called then correct logic for different values of config.aws.saml are followed."() {
        given:
        explicitlyMockPipelineVariable("AWS_USERNAME")
        explicitlyMockPipelineVariable("AWS_PASSWORD")

        when:
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)
        terraformDeployment.config = config
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.deploy()

        then:
        expectedCalls * getPipelineMock("echo")('Using saml2aws...')

        where:
        config                                             | expectedCalls
        [:]                                                | 0
        [aws: [saml: false]]                               | 0
        [aws: [saml: true, account: 'acc', rolename: 'role', credentialsId: 'cred']] | 1
    }

   def """When withEnv is configured in the phase, validate if it is propagated to the execution environment"""() {
        given:
        explicitlyMockPipelineVariable("AWS_USERNAME")
        explicitlyMockPipelineVariable("AWS_PASSWORD")

        when:
        def terraformDeployment = new TerraformDeployment(script: script, psc: psc)
        terraformDeployment.config = [deploymentType : 'terraform',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                terraform      : [
                        version: 'version'
                ],
                terragrunt     : [
                        version: 'version'
                ],
                aws            : [
                        saml         : true,
                        credentialsId: 'a',
                        account      : 'a',
                        rolename     : 'a',
                ],
                directories    : [
                        [
                                directory: 'directory',
                        ]
                ],
                withEnv         : ['somevar=somevalue']
        ]
        simulatePodTemplate(psc, terraformDeployment, cloudName)
        terraformDeployment.deploy()

        then:
        1 * getPipelineMock("withEnv").call(_) >> { _arguments ->
            def envArgs = [
                    'TF_LOG=', 'somevar=somevalue'
            ]
            assert envArgs == _arguments[0][0]
        }
    }
}
