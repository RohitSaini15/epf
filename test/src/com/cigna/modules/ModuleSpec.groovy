package com.cigna.modules

import com.cigna.SinglePodTest
import com.cigna.common.exception.ErrorStepException
import com.cigna.state.PipelineStateContext
import groovy.json.JsonOutput
import org.jenkinsci.plugins.credentialsbinding.impl.StringBinding
import org.jenkinsci.plugins.credentialsbinding.impl.UsernamePasswordBinding

import static com.cigna.common.utils.Utils.explainExecutionPlan

class ModuleSpec extends SinglePodTest {
    public static final String cloudName = 'notNull'
    PipelineStateContext psc

    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
        initScriptAndPsc()
    }

    def """When validate is called and required config items are missing, issues are raised"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType   : moduleType,
            branchPattern: '.*',
            moduleName   : step,
            cloudName    : cloudName
        ], psc: psc)

        def issues = module.validate()

        then:
        issues.size() == numberOfIssues

        where:
        moduleType << [null, 'stuff']
        step << [null, 'stuff']
        numberOfIssues << [2, 0]
    }

    def """When the run method is called and a config is provided the init
        container is set correctly and the proper command runs"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType   : 'initialize',
            moduleName   : 'cnp-image-validation',
            branchPattern: 'release-branch',
            cloudName    : cloudName
        ], psc: psc)

        setupModule(psc, module)
        module.run()

        then:
        1 * getPipelineMock("sh")({
            it.script ==~ /^cnp-validate-images.sh.*/
        })
    }

    def """When the run method is called and a config is provided the quality
        container is set correctly and the proper command runs"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType   : 'quality',
            moduleName   : 'cnp-quality-profile-checkmarx',
            branchPattern: 'release-branch',
            cloudName    : cloudName
        ], psc: psc)

        setupModule(psc, module)
        module.run()

        then:
        1 * getPipelineMock("sh")({
            it.script ==~ /^cnp-quality-profile-checkmarx.sh.*/
        })
    }

    def """When the run method is called and a config is provided the pcf
        container is set correctly and the proper command runs"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType   : 'pcf',
            moduleName   : 'cnp-deploy-pcf',
            branchPattern: 'release-branch',
            cloudName    : cloudName
        ], psc: psc)

        setupModule(psc, module)
        module.run()

        then:
        1 * getPipelineMock("echo").call({ it.startsWith("Switching to container 'cnp-docker-pcf") })
        1 * getPipelineMock("sh")({
            it.script ==~ /^cnp-deploy-pcf.sh.*/
        })
    }

    private void setupModule(PipelineStateContext localPsc, Module module, def theScript = script) {
        simulatePodTemplate(localPsc, module, cloudName)
    }

    def """arbitrary bring-your-own module is defined"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType   : 'go',
            branchPattern: '.*',
            cloudName    : cloudName,
            module       : [
                contractName: 'DEPLOY',
                stageName   : 'PCF',
                commandName : "module-build-go.sh",
                image       : 'repository/module/module-docker-go',
                version     : '1.0.0'
            ],
            args         : [
                targetPath : "module/PACKAGE/usr/local/bin",
                programName: "sample-go-app",
                srcPath    : "."
            ]
        ], psc: psc)

        setupModule(psc, module)
        psc.metadata.put("publishUrl", "https://cigna.jfrog.io/artifactory/libs-release-local/com/esrx/devops-testapps-SpringPcf/1.3.42/devops-testapps-SpringPcf-1.3.42.jar")
        module.run()

        then:
        1 * getPipelineMock("sh")({
            it.script.startsWith 'module-build-go.sh'
        })
    }

    def """when multiple credentials are calculated, it returns a flattened list of all"""() {
        given:
        def module = new Module(script: script, config: [
            moduleType   : 'go',
            branchPattern: '.*',
            module       : [
                commandName: "module-build-go.sh",
                image      : 'module/module-docker-go',
                version    : '1.0.0'
            ],
            cloudName    : cloudName,
            args         : [
                targetPath     : "module/PACKAGE/usr/local/bin",
                programName    : "sample-go-app",
                srcPath        : ".",
                requiresUnStash: true,
                stashName      : 'test-stash',
                stashPattern   : '.*.groovy',
                stashExcludes  : 'target',
                credentials    : [
                    [
                        type  : 'string',
                        id    : 'test-cred',
                        prefix: 'ARTIFACTORY',
                        env   : 'dev',
                        scope : 'OC',
                    ],
                    [
                        type  : 'usernamePassword',
                        id    : 'test-cred-file',
                        prefix: 'ARTIFACTORY',
                        env   : 'dev',
                        scope : 'EKS',
                    ],
                    [
                        id    : 'test-cred-default',
                        prefix: 'OC',
                        env   : 'stage',
                        scope : 'AWS',
                    ],
                ],
            ],
        ], psc: psc)

        when:
        setupModule(psc, module)
        module.run()

        then:
        2 * getPipelineMock('usernamePassword.call')(*_) >> { args ->
            new UsernamePasswordBinding(args[0].usernameVariable, args[0].credentialsId)
        }
        2 * getPipelineMock('string.call')(*_) >> { args ->
            new StringBinding(args[0].variable, args[0].credentialsId)
        }
        1 * getPipelineMock("sh").call({ it.script.startsWith 'module-build-go.sh' })
    }

    def """module repository loading can get values from the environment"""() {
        given:
        script.env.addValue('CNP_OC_CRED_DEV', 'not-a-real-cred')

        def localPsc = new PipelineStateContext(script)
        def module = new Module(script: script, config: [
            moduleType   : 'openshift',
            moduleName   : 'cnp-deploy-argorollouts',
            subCommand   : 'deploy',
            branchPattern: '.*',
            args         : [:]
        ], psc: localPsc)

        when:
        setupModule(localPsc, module, script)
        module.run()

        then:
        // since this module uses env.CNP_OC_CRED_DEV by default, if that key has a value in the environment,
        // then it will be used
        1 * getPipelineMock('usernamePassword.call')(['credentialsId'   : 'not-a-real-cred',
                                                      'passwordVariable': 'CNP_DEPLOY_ARGOROLLOUTS_CRED',
                                                      'usernameVariable': 'CNP_DEPLOY_ARGOROLLOUTS_USER'])
        1 * getPipelineMock("usernamePassword.call")(['credentialsId'   : 'not-a-real-cred',
                                                      'passwordVariable': 'CNP_DEPLOY_ARGOROLLOUTS_MODULE_PASSWORD',
                                                      'usernameVariable': 'CNP_DEPLOY_ARGOROLLOUTS_MODULE_USER'])
    }

    def """override cred var name"""() {
        given:
        def module = new Module(script: script, config: [
            moduleType   : 'openshift',
            moduleName   : 'cnp-deploy-argorollouts',
            subCommand   : 'deploy',
            branchPattern: '.*',
            args         : [
                credentials: [
                    [id: 'mycred', usernameVariable: 'myuser', passwordVariable: 'mypass'],
                    [id: 'anothercred', type: "string"],
                ],
            ]
        ], psc: psc)
        when:
        setupModule(psc, module)
        module.run()

        then:
        2 * getPipelineMock('usernamePassword.call')(['credentialsId'   : 'mycred',
                                                      'passwordVariable': 'mypass',
                                                      'usernameVariable': 'myuser'])
        1 * getPipelineMock("string.call")(['credentialsId': 'anothercred',
                                            'variable'     : 'CNP_DEPLOY_ARGOROLLOUTS_TOKEN'])
        1 * getPipelineMock("string.call")(['credentialsId': 'anothercred',
                                            'variable'     : 'CNP_DEPLOY_ARGOROLLOUTS_MODULE_TOKEN'])
    }

    def """module repository loading will use the variable name as the value if the value isn't set in the environment"""() {
        given:
        script.env.addValue('CNP_OC_CRED_DEV', '')

        def localPsc = new PipelineStateContext(script)
        def module = new Module(script: script, config: [
            moduleType   : 'openshift',
            moduleName   : 'cnp-deploy-argorollouts',
            subCommand   : 'deploy',
            branchPattern: '.*',
            args         : [:]
        ], psc: localPsc)

        when:
        setupModule(localPsc, module, script)
        module.run()

        then:

        // when the environment value is an empty string, use the key
        1 * getPipelineMock('usernamePassword.call')(['credentialsId'   : 'CNP_OC_CRED_DEV',
                                                      'passwordVariable': 'CNP_DEPLOY_ARGOROLLOUTS_CRED',
                                                      'usernameVariable': 'CNP_DEPLOY_ARGOROLLOUTS_USER'])
        1 * getPipelineMock("usernamePassword.call")(['credentialsId'   : 'CNP_OC_CRED_DEV',
                                                      'passwordVariable': 'CNP_DEPLOY_ARGOROLLOUTS_MODULE_PASSWORD',
                                                      'usernameVariable': 'CNP_DEPLOY_ARGOROLLOUTS_MODULE_USER'])
        // when the key isn't in the environment at all, use the key
        1 * getPipelineMock("string.call").call(['credentialsId': 'somecred', 'variable': 'CNP_DEPLOY_ARGOROLLOUTS_MODULE_TOKEN'])
        1 * getPipelineMock("string.call").call(['credentialsId': 'somecred', 'variable': 'CNP_DEPLOY_ARGOROLLOUTS_TOKEN'])
    }

    def """when PipelineMetadata contains entries they are added to commandName args, including all args with metadata lookups"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType   : 'go',
            branchPattern: '.*',
            module       : [
                contractName: 'DEPLOY',
                stageName   : 'PCF',
                commandName : "module-build-go.sh",
                image       : 'repository/module/module-docker-go',
                version     : '1.0.0'
            ],
            cloudName    : cloudName,
            args         : [
                targetPath : "module/PACKAGE/usr/local/bin",
                programName: "sample-go-app",
                srcPath    : ".",
                imageTag   : 'lookup:tagName',
                imageName  : 'lookup:imageName'
            ]
        ], psc: psc)

        setupModule(psc, module)
        psc.metadata.put('artifact', 'a long url', 'epf-build-maven', false)
        psc.metadata.put('tagName', '1.2.3', 'cnp-build-image', false)
        psc.metadata.put('imageName', 'image_name', 'cnp-build-image', false)
        module.run()

        then:
        1 * getPipelineMock("sh").call({ it.script.startsWith 'module-build-go.sh' })
    }

    def """module buildimage defined"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType    : 'docker',
            branchPattern : '.*',
            moduleName    : 'cnp-build-image',
            subCommand    : 'buildimage',
            cloudName     : cloudName,
            metadataInArgs: true // for artifact to be auto propagated to a module if not explicitly set and is available in metadata
        ], psc: psc)

        setupModule(psc, module)
        psc.metadata.put('artifact', 'a long url', 'epf-build-maven', false)
        module.run()

        then:
        1 * getPipelineMock("sh")({
            it.script.startsWith('cnptools buildimage') &&
                it.script.contains('"artifact":"a long url"')
        })
    }

    def "buildimage not defined as module"() {
        when:
        def module = new Module(script: script, config: [
            moduleType            : 'docker',
            branchPattern         : '.*',
            sdlcEnvironment       : 'dev',
            isProductionDeployment: false,
            moduleName            : 'cnp-build-image',
            subCommand            : 'buildimage',
            cloudName             : cloudName,
            args                  : [
                credentials        : [
                    [id: 'env.CNP_REGISTRY_DEV_CRED'],
                ],
                generateDockerFile : true,
                org                : 'cnp',
                appendCommitIdToTag: true,
                artifact           : 'lookup:publishUrl',
                registryName       : 'http://registry-dev.cigna.com'
            ],
        ], psc: psc)

        setupModule(psc, module)
        psc.metadata.put('artifact', 'a long url', 'epf-build-maven', false)
        module.run()

        then:
        1 * getPipelineMock("sh").call(*_)
    }

    def "rollouts cutover"() {
        when:
        def module = new Module(script: script, config: [
            moduleType            : 'openshift',
            moduleName            : 'cnp-deploy-argorollouts',
            subCommand            : 'promote',
            branchPattern         : '.*',
            sdlcEnvironment       : 'dev',
            isProductionDeployment: false,
            cloudName             : cloudName,
            args                  : [
                credentials          : [
                    [id: 'env.CNP_OC_CRED_DEV', env: 'dev'],
                ],
                appName              : 'springpcf-testapp-1-dev',
                configDir            : 'dev',
                namespace            : 'pipeline-automation',
                progressingRetryCount: 10,
                cluster              : 'hs-1-nonprod',
                platform             : 'OpenShift',
                env                  : 'dev'
            ]
        ], psc: psc)

        setupModule(psc, module)
        module.run()

        then:
        1 * getPipelineMock("sh").call(*_)
    }

    def "credentials can come from module repo when args are specified"() {
        given:
        def module = new Module(script: script, config: [
            moduleType            : 'openshift',
            moduleName            : 'cnp-deploy-argorollouts',
            subCommand            : 'promote',
            branchPattern         : '.*',
            sdlcEnvironment       : 'dev',
            isProductionDeployment: false,
            cloudName             : cloudName,
            args                  : [
                appName              : 'springpcf-testapp-1-dev',
                configDir            : 'dev',
                namespace            : 'pipeline-automation',
                progressingRetryCount: 10,
                cluster              : 'hs-1-nonprod',
                platform             : 'OpenShift',
                env                  : 'dev'
            ]
        ], psc: psc)

        when:
        setupModule(psc, module)
        module.configureModule()

        then:
        // there should be credentials based on the contract
        module.bridge.moduleContract.credentials.size() > 0
    }

    def "when no credentials in contract or args, bridge creds list is empty"() {
        given:
        def module = new Module(script: script, config: [
            moduleType   : 'initialize',
            moduleName   : 'cnp-image-validation',
            branchPattern: '.*',
            args         : [appName: 'springpcf-testapp-1-dev'],
            cloudName    : cloudName
        ], psc: psc)

        when:
        setupModule(psc, module)

        then:
        // there should be no credentials
        module.bridge.moduleContract.credentials.size() == 0
    }

    def "credentials can come from module repo when args aren't specified"() {
        given:
        def module = new Module(script: script, config: [
            moduleType            : 'openshift',
            moduleName            : 'cnp-deploy-argorollouts',
            subCommand            : 'promote',
            branchPattern         : '.*',
            sdlcEnvironment       : 'dev',
            isProductionDeployment: false,
            cloudName             : cloudName
        ], psc: psc)

        when:
        setupModule(psc, module)

        then:
        // there should be credentials based on the contract
        module.bridge.moduleContract.credentials.size() > 0
    }

    def "credentials in args override those on contract"() {
        given:
        def module = new Module(script: script, config: [
            moduleType            : 'openshift',
            moduleName            : 'cnp-deploy-argorollouts',
            subCommand            : 'promote',
            branchPattern         : '.*',
            sdlcEnvironment       : 'dev',
            isProductionDeployment: false,
            cloudName             : cloudName,
            args                  : [
                credentials: [[id: 'this-is-a-cred']]
            ]
        ], psc: psc)

        when:
        setupModule(psc, module)

        then:
        // there should be credentials based on the args
        module.bridge.moduleContract.credentials.size() == 1
        module.bridge.moduleContract.credentials[0].id == 'this-is-a-cred'
    }

    def "when no module name, use command name up to first period"() {
        given:
        def module = new Module(script: script, config: [
            moduleType   : 'go',
            branchPattern: '.*',
            cloudName    : cloudName,
            module       : [
                contractName: 'DEPLOY',
                stageName   : 'PCF',
                commandName : "module-build-go.sh",
                image       : 'repository/module/module-docker-go:1.0.0',
            ]
        ], psc: psc)

        when:
        setupModule(psc, module)

        then:
        // there should be credentials based on the args
        module.bridge.moduleContract.moduleName == 'module-build-go'
    }

    def '''verify buildDefaultEnvironment picks up default environment'''() {
        given:
        // the expected default environment, but all the images have their tags removed
        def expectedEnvStarts = [
            'CNP_DEFAULT_DOCKER_IMAGE=cnp/cnp-docker-core:',
            'CNP_DEFAULT_K8S_IMAGE=cnp/cnp-docker-k8s:',
            'CNP_DEFAULT_DOCKER_BUILDER_IMAGE=cnp/cnp-docker-podman:',
            'CNP_DEFAULT_GO_IMAGE=cnp/cnp-docker-go:',
            'CNP_DEFAULT_INIT_IMAGE=cnp/cnp-docker-init:',
            'CNP_DEFAULT_NPM_IMAGE=cnp/cnp-docker-node10:',
            'CNP_DEFAULT_TERRAFORM_IMAGE=cnp/cnp-docker-terraform:',
            'CNP_DEFAULT_AWS_IMAGE=cnp/cnp-docker-aws:',
            'CNP_DEFAULT_PCF_IMAGE=cnp/cnp-docker-pcf:',
            'EPF_DEFAULT_ANSIBLE_IMAGE=cnp/epf-docker-ansible:',
            'CNP_DEFAULT_JAVA_IMAGE=cnp/cnp-docker-maven-java8:',
            'CNP_DEFAULT_GRADLE_IMAGE=cnp/cnp-docker-gradle-java8:',
            'CNP_DEFAULT_DOJO_MSG_IMAGE=cnp/cnp-dojo-messenger:',
            'CNP_DEFAULT_ACS_IMAGE=cpe/acs-cli:',
            'GIT_CREDENTIAL=gitcred',
            'ARTIFACTORY_CREDENTIAL=cbc/artifactory',
            'NPM_CREDENTIALS_ID=npm-artifactory-id',
            'ARTIFACTORY_ROOT_URL=https://cigna.jfrog.io/artifactory',
            'IMAGE_VALIDATION_MODULE_COMMAND=cnp-validate-images.sh',
            'CNP_CIGNA_GIT=somecred',
            'JOB_BUILD_CRED=testcallid',
            'CX_ENDPOINT=https://ch3qw1017757.accounts.root.corp',
            'CX_SERVER_ADDRESS=https://10.222.239.180/',
            'SONAR_CREDENTIAL_ID=sonar',
            'SONAR_HOST=https://sonarqube.sys.cigna.com/',
            'CX_CREDENTIAL=d2d7833b-38fc-405a-b1b5-4f573ac788a6',
            'GIT_TOKEN=GIT_TOKEN',
            'CNP_ASN_VALIDATE_ENABLED=false',
            'CNP_XLR_TEMPLATE_URL=https://xlrelease.express-scripts.com/api/v1/templates/Applications/templates/Folder2a9c42e13b784e8fbb57c559852b807c/Release47c3527db6944b918488c5c90166c4b0/start',
            'CNP_DISABLE_JIRASCAN=true',
            'CNP_CALLBACK_JOB=https://orchestrator1.orchestrator-v2.sys.cigna.com/job/orchestrators-folders/job/adjudicator/job/Production/job/DevOps/job/EPF/job/XLRCallbackHandler/',
            'CNP_CANDIDATE_JOB=orchestrators-folders/job/adjudicator/job/Production/job/DevOps/job/EPF/job/CandidateDeployer',
            'AUTHORIZED_USERS=xlrelease,internal\\svp_pipeline_admin,accounts\\ei0733'
        ]
        def module = new Module(script: script, config: [:], psc: psc)
        script.env.JOB_NAME = 'orchestrators-folders/adjudicator/Adjudicator/main'
        when:
        def envList = module.buildDefaultEnvironment()
        then:
        expectedEnvStarts.size() == envList.size()
        // for each pairwise comparson, the actual 'starts with' the expected
        [envList, expectedEnvStarts].transpose().each { pair ->
            assert pair[0].startsWith(pair[1])
        }
    }

    def '''verify buildDefaultEnvironment picks up overridden environment'''() {
        given:
        // default environment with image tags removed (and the override)
        def expectedEnvStarts = [
            'CNP_DEFAULT_DOCKER_IMAGE=cnp/cnp-docker-core:',
            'CNP_DEFAULT_K8S_IMAGE=cnp/cnp-docker-k8s:',
            'CNP_DEFAULT_DOCKER_BUILDER_IMAGE=cnp/cnp-docker-podman:',
            'CNP_DEFAULT_GO_IMAGE=cnp/cnp-docker-go:',
            'CNP_DEFAULT_INIT_IMAGE=cnp/cnp-docker-init:',
            'CNP_DEFAULT_NPM_IMAGE=cnp/cnp-docker-node10:',
            'CNP_DEFAULT_TERRAFORM_IMAGE=cnp/cnp-docker-terraform:',
            'CNP_DEFAULT_AWS_IMAGE=cnp/cnp-docker-aws:',
            'CNP_DEFAULT_PCF_IMAGE=cnp/cnp-docker-pcf:',
            'EPF_DEFAULT_ANSIBLE_IMAGE=cnp/epf-docker-ansible:',
            'CNP_DEFAULT_JAVA_IMAGE=cnp/cnp-docker-maven-java8:',
            'CNP_DEFAULT_GRADLE_IMAGE=cnp/cnp-docker-gradle-java8:',
            'CNP_DEFAULT_DOJO_MSG_IMAGE=cnp/cnp-dojo-messenger:',
            'CNP_DEFAULT_ACS_IMAGE=cpe/acs-cli:',
            'GIT_CREDENTIAL=gitcred',
            'ARTIFACTORY_CREDENTIAL=cbc/artifactory',
            'NPM_CREDENTIALS_ID=npm-artifactory-id',
            'ARTIFACTORY_ROOT_URL=https://cigna.jfrog.io/artifactory',
            'IMAGE_VALIDATION_MODULE_COMMAND=cnp-validate-images.sh',
            'CNP_CIGNA_GIT=somecred',
            'JOB_BUILD_CRED=testcallid',
            'CX_ENDPOINT=https://ch3qw1017757.accounts.root.corp',
            'CX_SERVER_ADDRESS=https://10.222.239.180/',
            'SONAR_CREDENTIAL_ID=sonar',
            'SONAR_HOST=https://sonarqube.sys.cigna.com/',
            "CX_CREDENTIAL=${override ?: 'd2d7833b-38fc-405a-b1b5-4f573ac788a6'}",
            'GIT_TOKEN=GIT_TOKEN',
            'CNP_ASN_VALIDATE_ENABLED=false',
            'CNP_XLR_TEMPLATE_URL=https://xlrelease.express-scripts.com/api/v1/templates/Applications/templates/Folder2a9c42e13b784e8fbb57c559852b807c/Release47c3527db6944b918488c5c90166c4b0/start',
            'CNP_DISABLE_JIRASCAN=true',
            'CNP_CALLBACK_JOB=https://orchestrator1.orchestrator-v2.sys.cigna.com/job/orchestrators-folders/job/ba13975/job/hc360-populations-api/job/Production/job/DevOps/job/EPF/job/XLRCallbackHandler/',
            'CNP_CANDIDATE_JOB=orchestrators-folders/job/ba13975/job/hc360-populations-api/job/Production/job/DevOps/job/EPF/job/CandidateDeployer',
            'AUTHORIZED_USERS=xlrelease,internal\\svp_pipeline_admin,accounts\\ei0733'
        ]
        script.env.CX_CREDENTIAL = override
        script.env.JOB_NAME = 'orchestrators-folders/ba13975/hc360-populations-api/Non-Production/hc360-populations-api/release%2Fv1.7.0'
        def module = new Module(script: script, config: [:], psc: psc)
        when:
        def envList = module.buildDefaultEnvironment()

        then:
        // the expected env and the actual env have the same counts
        expectedEnvStarts.size() == envList.size()
        // for each pairwise comparison, the actual 'starts with' the expected
        [envList, expectedEnvStarts].transpose().each { pair ->
            assert pair[0].startsWith(pair[1])
        }
        where:
        override << [null, 'override', 'another-value']
    }

    def """verify lookup will work in any config element and can handle partially matched values correctly"""() {
        when:
        def module = new Module(script: script, config: [
            moduleType   : 'go',
            branchPattern: '.*',
            cloudName    : cloudName,
            module       : [
                contractName: 'DEPLOY',
                stageName   : 'PCF',
                commandName : "module-build-go.sh",
                image       : 'repository/customer-lookup:1.0.0',
            ],
            args         : [
                targetPath : "module/PACKAGE/usr/local/bin",
                programName: "sample-go-app",
                srcPath    : ".",
                imageTag   : 'lookup:tagName',
                imageName  : 'lookup:imageName',
                artifact   : 'lookup:epf-build-maven:artifact',
                artifact1  : 'lookup:artifact',
                buildArgs  : 'ARTIFACT=lookup:{imageName}:lookup:{tagName}'
            ]
        ], psc: psc)

        psc.metadata.put('artifact', 'a long url', 'epf-build-maven', false)
        psc.metadata.put('tagName', '1.2.3', 'cnp-build-image', false)
        psc.metadata.put('imageName', 'image_name', 'cnp-build-image', false)
        module.config = module.updateAllLookupEntries(module.config)

        then:
        assert module.config.args.imageTag == '1.2.3'
        assert module.config.args.imageName == 'image_name'
        assert module.config.args.artifact == 'a long url'
        assert module.config.args.artifact1 == 'a long url'
        assert module.config.args.buildArgs == 'ARTIFACT=image_name:1.2.3'
        assert module.config.module.image == 'repository/customer-lookup:1.0.0'
    }

    def '''Find folder-root from Job Name'''() {
        when:
        def folderName = Module.extractFolderRoot(name)
        then:
        folderName == expectedName
        where:
        name << [
            'orchestrators-folders/hs-pipeline/Functional Test Apps/devops-testapps-SpringPcf-EPF/release%2Fconducive',
            'orchestrators-folders/epf/job/epf-test-apps/job/Enterprise%20Pipeline%20Framework%20-%20Sample%20GoLang%20Test%20Application/job/feature%252Fmultilibrary/',
            'orchestrators-folders/adjudicator/job/Adjudicator/job/main/',
            'orchestrators-folders/ba13975/hc360-populations-api/Non-Production/hc360-populations-api/release%2Fv1.7.0',
            'orchestrators-folders/BA13975/hc360-populations-api/Non-Production/hc360-populations-api/release%2Fv1.7.0',
            'orchestrators-folders/BA13975890/hc360-populations-api/Non-Production/hc360-populations-api/release%2Fv1.7.0'
        ]
        expectedName << ['hs-pipeline', 'epf', 'adjudicator', 'ba13975/hc360-populations-api', 'BA13975/hc360-populations-api', 'BA13975890/hc360-populations-api']
    }

    def '''test additional info sdlcEnvironment and subCommand in displayName'''() {
        def config = [:]
        given:
        config = [
            moduleType   : 'openshift',
            moduleName   : 'cnp-deploy-argorollouts',
            branchPattern: '.*',
            cloudName    : cloudName
        ] + scenario

        when:
        Module module = new Module(script: script, config: config, psc: psc)
        setupModule(psc, module)
        def allPhases = config
        def results = explainExecutionPlan(psc, [cloudName], [allPhases])
        then:
        results.toLowerCase().contains(expectedResult)
        where:
        scenario << [[:], ['sdlcEnvironment': ''], ['sdlcEnvironment': 'dev'], ['sdlcEnvironment': 'dev', 'subCommand': 'promote'], ['sdlcEnvironment': 'dev', 'module': ['subCommand': 'deploy', moduleType: 'openshift', image: 'cnp/cnp-docker-k8s:1.1.4', moduleName: 'cnp-deploy-argorollouts']]]
        expectedResult << ['deploy)', 'deploy)', 'deploy dev)', 'promote dev)', 'deploy dev)']
    }

    def '''verify module info outputted'''() {
        def config = [:]
        given:
        config = [
            moduleType   : 'openshift',
            moduleName   : 'cnp-deploy-argorollouts',
            branchPattern: '.*',
            cloudName    : cloudName
        ]
        when:
        Module module = new Module(script: script, config: config, psc: psc)
        setupModule(psc, module)
        module.run()
        then:
        1*getPipelineMock("echo")({it.contains('========== MODULE INFO ==========')})
    }


    private final Map genericModule = [
        moduleType: 'openshift',
        module    : [
            moduleName : 'generic-module',
            image      : 'cnp/generic-module:1.0.0',
            commandName: 'some-script.sh'
        ],
        cloudName : cloudName
    ]

    def '''Successful module outcome is based on results file when present (exit code is irrelevant)'''() {
        given:
        def moduleToTest = new Module(script: script, config: genericModule, psc: psc)
        def resultFileName = 'generic-module-results.json'
        def resultFileContent = JsonOutput.toJson([commandResult: 'SUCCESS',
                                                   commandOutput: ['stuff': 'some output']])
        setupModule(psc, moduleToTest)
        when:
        moduleToTest.run()
        then:
        1 * getPipelineMock('sh').call({ it.returnStatus }) >> exitCode
        2 * getPipelineMock("fileExists").call(resultFileName) >> true
        getPipelineMock("readFile").call(resultFileName) >> resultFileContent
        psc.metadata.get('generic-module:stuff') == 'some output'
        noExceptionThrown()
        where:
        //noinspection GroovyAssignabilityCheck
        exitCode << [null, 0, 5, -5]
    }

    def '''Failure module outcome is based on results file when present (exit code is irrelevant)'''() {
        given:
        def moduleToTest = new Module(script: script, config: genericModule, psc: psc)
        def resultFileName = 'generic-module-results.json'
        def resultFileContent = JsonOutput.toJson([commandResult: 'FAILURE',
                                                   commandOutput: ['stuff': 'some output'],
                                                   errors       : ['oh no!']])
        setupModule(psc, moduleToTest)
        when:
        moduleToTest.run()
        then:
        1 * getPipelineMock('sh').call({ it.returnStatus }) >> exitCode
        2 * getPipelineMock("fileExists").call(resultFileName) >> true
        getPipelineMock("readFile").call(resultFileName) >> resultFileContent
        psc.metadata.get('generic-module:stuff') == 'some output'
        def ex = thrown(ErrorStepException)
        assert ex.message.contains('oh no!')
        where:
        //noinspection GroovyAssignabilityCheck
        exitCode << [null, 0, 5, -5]
    }

    def '''With no results file, null or 0 exit code signals module success'''() {
        given:
        def moduleToTest = new Module(script: script, config: genericModule, psc: psc)
        setupModule(psc, moduleToTest)
        when:
        moduleToTest.run()
        then:
        1 * getPipelineMock('sh').call({ it.returnStatus }) >> exitCode
        2 * getPipelineMock('fileExists').call('generic-module-results.json') >> false
        noExceptionThrown()
        where:
        //noinspection GroovyAssignabilityCheck
        exitCode << [null, 0]
    }

    def '''With no results file, non-zero integer exit code signals module failure'''() {
        given:
        def moduleToTest = new Module(script: script, config: genericModule, psc: psc)
        setupModule(psc, moduleToTest)
        when:
        moduleToTest.run()
        then:
        1 * getPipelineMock('sh').call({ it.returnStatus }) >> exitCode
        1 * getPipelineMock("fileExists").call('generic-module-results.json') >> false
        thrown(ErrorStepException)
        where:
        //noinspection GroovyAssignabilityCheck
        exitCode << [5, -5, 1, 127, Integer.MAX_VALUE, Integer.MIN_VALUE]
    }

}