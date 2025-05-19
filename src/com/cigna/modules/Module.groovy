package com.cigna.modules

import com.cigna.base.Phase
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.StashUtils
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.DockerUri
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.steps.CreateStepResultFromFileStep
import com.evernorth.cloudnativebuild.service.ModuleUtil
import groovy.json.JsonBuilder

import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.cigna.common.utils.Utils.*

/**
 * Loadable Module phase implementation that allows for running of standalone modules.*/
class Module extends Phase {
    Module() {
        containerName = ''
        containerImage = ''
        containerVersion = ''
        baseValidationItems = ['moduleType']
        groupID = 'module'
        containerMemory = '500Mi'
        containerCpu = '500m'
    }

    String moduleCommand
    ModuleBridge bridge

    @NonCPS
    @Override
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        if (config?.module) {
            additionalValidationItems += [
                [testString: 'module.image', customMessage: 'module contract must define an image.'],
                [testString: 'module.commandName', customMessage: "module contract must have a 'commandName' entry point."],
            ]
        } else {
            additionalValidationItems += [[testString: 'moduleName', customMessage: "module must have a 'moduleName' entry point."]]
        }
        super.validate(requiresBranchPattern, phase)
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = []
        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            1000,
            500,
            1000,
            env
        )

        additionalPodConfig = [
            containers: [containerTemplate.getContainer(containerName)]
        ]

        if (!this.configureModule()) {
            script.echo("Execution of phase $config.moduleType was deferred")
            return false
        }

        if (config?.container) {
            updatePhaseContainer(config?.container)
        }

        super.prePodConfig()
    }

    void run(
        List credsList = [],
        List configsList = []
    ) {
        credsList += mapCredentials()
        List envList = config?.withEnv ?: []
        envList += ['HOME=/tmp', "CNP_DISABLE_JIRASCAN=${script.env.CNP_DISABLE_JIRASCAN ?: true}"]
        envList += buildDefaultEnvironment()

        script.stage(generateStageName(configStageName())) {
            psc.podSelector.select(psc, podTemplateContainerName, cloud(config)) {
                script.withCredentials(credsList) {
                    script.configFileProvider(configsList) {
                        script.withEnv(envList) {
                            tagCommit()
                            moveFiles('begin')
                            runModule()
                            moveFiles('end')
                        }
                    }
                }
            }
        }
    }

    protected Boolean configureModule() {
        def moduleToRun
        bridge = this.newModuleBridge()
        if (config.containsKey('module')) {
            moduleToRun = parseModuleContract()
            script.echo "moduleToRun is obtained by parsing ${config}"
        } else {
            moduleToRun = findModuleInRepository(config.moduleType, config.moduleName, config?.subCommand ?: '')
            script.echo "moduleToRun is obtained from default modules for '${config.moduleType}:${config.moduleName}:${config.subCommand}'"
        }
        if (moduleToRun == null) {
            throw moduleException()
        }
        manageModuleResourceAllocations(moduleToRun)

        moduleToRun.subCommand = moduleToRun.subCommand ?: config.subCommand ?: ''
        Map<String, Object> argsOrEmpty = config.args as Map<String, Object> ?: [:]
        moduleToRun = bridge.parse(moduleToRun, argsOrEmpty)
        config.args = argsOrEmpty + bridge.releaseVariables

        updateContainerDetails(moduleToRun)

        script.echo buildDescription(bridge.moduleContract, true)

        this.moduleCommand = moduleToRun.commandName + (moduleToRun.subCommand.isEmpty() ? '' : ' ' + moduleToRun.subCommand)

        return true
    }

    protected void manageModuleResourceAllocations(ModuleContract moduleToRun) {
        // If a module def in MCR has limit overrides, we *might* apply them here
        // if the phase has a container override in the JF, that takes precedence.
        if (moduleToRun.limits.size() > 0) {
            // MCR has container limit overrides
            def cpu = 0
            def memory = 0
            if (moduleToRun.limits.containsKey('cpu')) {
                cpu = moduleToRun.limits.cpu
            }
            if (moduleToRun.limits.containsKey('memory')) {
                memory = moduleToRun.limits.memory
            }
            // there are no container defs at all, use the defaults
            if (!config.containsKey('container')) {
                config.container = [
                    cpu   : cpu,
                    memory: memory
                ]
                return
            }
            // we have moduleToRun limits and either one or both of cpu/memory is already defined in config.container
            if (cpu > 0 && !config.container.containsKey('cpu')) {
                config.container.cpu = moduleToRun.limits.cpu
            }
            if (memory > 0 && !config.container.containsKey('memory')) {
                config.container.memory = moduleToRun.limits.memory
            }
        }
    }

    void updateContainerDetails(ModuleContract moduleToRun) {
        def newName = PodConfigGenerator.getContainerName(moduleToRun.image)
        if (this.additionalPodConfig.containsKey('containers') && this.additionalPodConfig.containers.size() > 0) {
            this.additionalPodConfig.containers[0].name = newName
            this.additionalPodConfig.containers[0].image = moduleToRun.image
        } else {
            // we need to inject a container if there is none to override.
        }
        this.containerName = newName
        config.containerName = newName

        def details = DockerUri.imageAndTag(moduleToRun.image)
        config.phaseInstance.containerImage = details['image']
        config.phaseInstance.containerVersion = details['tag']
        config.containerImage = details['image']

    }

    protected ModuleContract findModuleInRepository(def moduleType, def moduleName, def subCommand = '') {
        def modules = psc.repository.defaultModules.find { moduleList ->
            moduleList.key == moduleType
        }?.value
        modules?.find { module -> module.moduleName == moduleName && (subCommand == '' || module.subCommand == subCommand)
        } ?: null
    }

    @NonCPS
    protected ModuleBridgeException moduleException() {
        return new ModuleBridgeException("Unable to load module identified with type '${config.moduleType}' and name '${config.moduleName}' and sub-command '${config.subCommand}'")
    }

    @NonCPS
    protected ModuleBridge newModuleBridge() {
        return new ModuleBridge(script, config)
    }

    protected void runModule() {
        script.dir(baseDirectory) {
            script.echo("---> START MODULE (${bridge.moduleContract.contractName}:${bridge.moduleContract.moduleName})")
            /**
             * XXX.cnm - TODO - Combine the requiresUnstash logic with phaseCache
             *         - TODO - if one is set coerce the other to align & vice versa
             */
            if (bridge.moduleContract.requiresUnStash) {
                String stashName = script.env.STASH_ID ?: bridge.moduleContract.stashName
                StashUtils.unstash(script, psc)
            }
            def args = processArguments(script, config)
            runImpl(args)
            injectResultsInMetadataCache()
            if (bridge.moduleContract.requiresStash) {
                configureStash(bridge.moduleContract)
                if (script.env.STASH_ID && script.env.STASH_ID != null && !script.env.STASH_ID.empty) {
                    script.echo("Stash ID ${script.env.STASH_ID} is already active, could result in data loss")
                }
                script.env.STASH_ID = StashUtils.stash(script, config, psc, stashIncludePattern, stashExcludePattern)
            }
            script.echo("---> END MODULE (${bridge.moduleContract.contractName}:${bridge.moduleContract.moduleName})")
        }
    }


    // phases that extend Module can override the default module execution by overriding runImpl()
    // (See Release.groovy for an example)
    protected void runImpl(def args) {
        def exitCode = script.sh(script: "$moduleCommand '$args'", returnStatus: true)
        // if there's a results file, then we will get our outcome from that, otherwise
        // we'll throw if the shell command's exit code isn't 0 (or null)
        if (!script.fileExists(bridge.moduleContract.logFileName) && exitCode) {
            throwErrorStepException("'$moduleCommand' exited with code $exitCode")
        }
    }

    protected String processArguments(def script, def config) {
        config.args = config.containsKey('args') ? config.args : [:]

        // If a field in args has the same name as a field in PipelineMetadata, we update it to contain the value
        if (psc.metadata.size() > 0) {
            // takes care of full string interpolation but not injecting metadata into args
            config = substituteConfigurationLookups(psc, config, script)
            if (FeatureFlags.debug) {
                script.echo("config.metadataInArgs in Module: ${config.metadataInArgs}")
            }
            psc.metadata.eachEntry { MetadataEntry entry ->
                String key = entry.key
                String value = entry.value.toString()
                Map m = [:]
                m[key] = value

                /**
                 * XXX.cnm - TODO - Need to take account of targeted environment to support named
                 *           TODO - attributes, for each environment*/
                if (config.args?.containsKey(key) && (config.args[key].toString()).contains('lookup:')) {
                    script.echo("module '${entry.moduleName}' arg '${key}' changing  from '${config.args[key as String]}' to '${value}'")
                    config.args[key] = "$value"
                } else if (!config.args?.containsKey(key) && config.metadataInArgs) {
                    config.args += m
                }
            }
        }
        config.args += bridge.postProcess()
        config = updateAllLookupEntries(config)
        generateOutput(config)
    }

    @NonCPS
    protected String generateOutput(Map<String, Object> config) {
        config?.args ? new JsonBuilder(config?.args).toString() : '{}'
    }

    @NonCPS
    protected ModuleContract parseModuleContract(Map phaseToParse = config) {
        [image       : phaseToParse.module.image ==~ /.*:.*/ ? phaseToParse.module.image : "${phaseToParse.module.image}:${phaseToParse.module.version}",
         commandName : phaseToParse.module.commandName,
         subCommand  : phaseToParse.module?.subCommand ?: '',
         contractName: phaseToParse.module?.contractName ?: '',
         stageName   : phaseToParse.module?.stageName ?: '',
         moduleName  : phaseToParse.module?.moduleName ?: '',]
    }

    protected void configureStash(ModuleContract moduleContract) {
        config.stashEnabled = true
        config.stashIncludePattern = moduleContract.stashPattern
        config.stashExcludePattern = moduleContract.excludesPattern
        config.stashName = moduleContract.stashName
    }

    /**
     * Should find a cleaner way to implement a more meaningfule display name depending on the underlying module
     * @param prefix
     * @return a string describing this Module instance
     */
    @Override
    def displayName(def prefix = '') {
        def simpleName = super.displayName(prefix)

        if (config?.moduleName == 'cnp-package-deb' || config?.module?.moduleName == 'cnp-package-deb') {
            "$prefix${config.args.packageRoot}"
        } else {
            "$prefix$simpleName(${configStageName()})"
        }
    }

    protected String configStageName() {
        def action = config?.module?.subCommand ?: config?.subCommand ?: bridge?.moduleContract?.contractName
        if (config.containsKey('sdlcEnvironment') && config.sdlcEnvironment && config.sdlcEnvironment != 'Undefined') {
            "${config.moduleType}: ${action} ${config.sdlcEnvironment}"
        } else {
            "${config.moduleType}: ${action}"
        }
    }

    protected List mapCredentials() {
        List credsList = []
        String envVarPrefix = "${bridge.moduleContract.moduleName.replace('-', '_').toUpperCase()}_"
        String environment = config?.args?.env ?: config?.sdlcEnvironment ?: 'dev'
        if (bridge.moduleContract.credentials?.size() > 0) {
            credsList += bridge.moduleContract.credentials.findAll {
                it.env == null || it.env == '' || it.env?.equalsIgnoreCase(environment)
            }.collect {
                Object[] credObj
                String envVarPrefixTemp = envVarPrefix
                if (it.prefix) {
                    envVarPrefixTemp = "${envVarPrefix}${it.prefix}_"
                }
                switch (it.type) {
                    case 'string':
                        credObj = [script.string(credentialsId: it.id,
                            variable: it.variable ?: "${envVarPrefixTemp}MODULE_TOKEN"),
                                   script.string(credentialsId: it.id,
                                       variable: it.variable ?: "${envVarPrefixTemp}TOKEN"),]
                        break
                    default:
                        credObj = [script.usernamePassword(credentialsId: it.id,
                            passwordVariable: it.passwordVariable ?: "${envVarPrefixTemp}MODULE_PASSWORD",
                            usernameVariable: it.usernameVariable ?: "${envVarPrefixTemp}MODULE_USER"),
                                   script.usernamePassword(credentialsId: it.id,
                                       passwordVariable: it.passwordVariable ?: "${envVarPrefixTemp}CRED",
                                       usernameVariable: it.usernameVariable ?: "${envVarPrefixTemp}USER"),]
                        break
                }

                script.echo("Credential : $credObj")

                credObj
            }
        }
        credsList.flatten()
    }

    @NonCPS
    static String buildDescription(ModuleContract contract, includeHeader = false) {
        StringBuilder builder = new StringBuilder()
        if (includeHeader) {
            builder.append("========== MODULE INFO ==========")
        }
        builder.append("\nName: ${contract.moduleName}")
        builder.append("\nContract: ${contract.contractName}")
        builder.append("\nCommand: ${contract.commandName}")
        builder.append("\nSubcommand: ${contract.subCommand}")
        builder.append("\nDocker Image: ${contract.image}")
        builder.append("\nRequired Credentials:${contract.credentials.toString()}")
        builder.append("\n=======================================")
        return builder.toString()
    }

    void injectResultsInMetadataCache() {
        // If the cnp module produced a $moduleName-results.json file, we inject the values into the
        // metadata cache.
        def result = createNewStepResult().execute(bridge.moduleContract)
        result.commandOutput.each { entry ->
            psc.metadata.put(
                entry.key as String, entry.value, bridge.moduleContract.moduleName, isSensitive(entry.key))
        }
        if (result.commandResult != StepResult.SUCCESS) {
            script.currentBuild.result = StepResult.FAILURE
            throw moduleError(result)
        }
    }

    @NonCPS
    protected ErrorStepException moduleError(StepResult result) {
        new ErrorStepException("Module Execution for '${bridge.moduleContract.moduleName}' failed, errors ${result.errors}")
    }

    @NonCPS
    protected CreateStepResultFromFileStep createNewStepResult() {
        new CreateStepResultFromFileStep(script)
    }

    @NonCPS
    static String extractFolderRoot(String jobName) {
        // case insensitive ba followed by 5 or more digits and /any folder name or just /any folder name
        def pattern = /(?i)orchestrators-folders\/((ba\d{5,}\/.*?)|(.*?))\/.*/
        // or the pattern should be /orchestrators-folders\/(.*?)\/Non-Production.*/ - assuming anything between orchestrators-folders & Non-Production
        Pattern p = Pattern.compile(pattern)
        Matcher m = p.matcher(jobName)
        m.matches() ? m.group(1) : ''
    }

    @NonCPS
    List buildDefaultEnvironment() {
        def masterName = ModuleUtil.getMasterName(script)
        def environment = psc.globalModuleManager.configuration().environment(script)

        def pathPrefix = config?.jobPathPrefix ?: 'job/Production/job/DevOps/job/EPF/job'
        def folderRootName = extractFolderRoot(script.env.JOB_NAME)?.replace('/', '/job/')

        def disableJirascan = config?.disableJirascan?.asBoolean() ?: true
        if (disableJirascan) {
            script.echo('Jira Scanning - DISABLED')
            environment.CNP_DISABLE_JIRASCAN = true
        } else {
            script.echo('Jira Scanning - ENABLED')
            environment.CNP_DISABLE_JIRASCAN = false
        }
        if (environment.CNP_XLR_TEMPLATE_URL) {
            script.echo("CNP_XLR_TEMPLATE_URL is configured to ${environment.CNP_XLR_TEMPLATE_URL}")
        } else {
            script.echo("CNP_XLR_TEMPLATE_URL is not configured, you will not be able to release the application")
        }
        environment.CNP_CALLBACK_JOB = "https://$masterName/job/orchestrators-folders/job/${folderRootName}/${pathPrefix}/XLRCallbackHandler/"
        script.echo("setting CNP_CALLBACK_JOB to https://$masterName/job/orchestrators-folders/job/${folderRootName}/${pathPrefix}/XLRCallbackHandler/")
        environment.CNP_CANDIDATE_JOB = "orchestrators-folders/job/${folderRootName}/${pathPrefix}/CandidateDeployer"
        script.echo("setting CNP_CANDIDATE_JOB to orchestrators-folders/job/${folderRootName}/${pathPrefix}/CandidateDeployer")

        environment.collect { "${it.key}=${it.value}" }
    }
}
