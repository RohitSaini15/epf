package com.cigna.common.phases

import com.cigna.base.DockerPipelineLib
import com.cigna.base.Phase
import com.cigna.builds.Build
import com.cigna.checkpoint.Checkpoint
import com.cigna.common.exception.InvalidInputException
import com.cigna.common.notification.Notification
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.deployment.Deployment
import com.cigna.freestyle.Freestyle
import com.cigna.linting.Linting
import com.cigna.modules.Module
import com.cigna.packaging.KanikoPackaging
import com.cigna.parallel.Parallel
import com.cigna.release.Release
import com.cigna.ruleengine.Ruleengine
import com.cigna.state.PipelineStateContext
import com.cigna.testing.Testing
import com.cigna.ticket.Ticket
import com.cloudbees.groovy.cps.NonCPS
import hudson.Functions

/**
 * Class contains the phase loading logic moved here during cignaBuildFlow refactoring.
 */
class PhaseLoader implements Serializable {
    Object script = null
    PipelineStateContext psc

    PhaseLoader(Object script, PipelineStateContext psc) {
        this.script = script
        this.psc = psc
    }

    def loadPhasesFromNamedMap(Map<String, Map<String, Object>> namedPhases) {
        List<String> issues = []
        def collectedPhases = namedPhases.collect { phase ->
            loadSinglePhase(phase.key, phase.value, null, issues)
        }
        [collectedPhases, issues]
    }

    def loadPhases(List<Map<String, Object>> phasesToRun, Notification notification) {
        List<String> issues = []
        def collectedPhases = phasesToRun.eachWithIndex { phase, index ->
            loadSinglePhase("Phase $index", phase, notification, issues)
        }

        [collectedPhases, issues]
    }

/**
 * This runs any and all tests/test types for a given deployment based on the given configuration
 *
 * @param superClass The intended parent of the class targeted for instantiation to ensure that it
 * properly conforms to the expected class hierarchy
 * @param classPackageName The package that the class targeted for instantiation resides in
 * @param classNamePrefix The type that the user specified in their configuration
 * @param classNameSuffix All types have a suffix such as Build, or Test, this is that suffix
 * @param instanceConfig The config map taken from the closure body in the call method
 * @param notification The notification object to instantiate the Phase class with
 *
 * @return an instance of a subclass of DockerPipelineLib that is instantiated and ready to use
 * based on given arguments
 */
    DockerPipelineLib loadInstance(Object superClass, String classPackageName, String classNamePrefix,
                                   String classNameSuffix, Map<String, Object> instanceConfig, Notification notification) {
        Object subClassHandle
        String altClassName = ''
        String altClassNamePrefix = ''
        Object newNotification
        if (classNamePrefix.capitalize() in ['Kaniko', 'Container', 'Docker']) {
            altClassNamePrefix = 'Kaniko'
            script.ansiColor('xterm') {
                script.echo("\033[31m***** ${KanikoPackaging.DEPRECATION_MSG} *****\033[0m")
            }
            psc.complianceValidator.addNewsArticle(KanikoPackaging.DEPRECATION_MSG)
        }
        if (classNameSuffix.capitalize() == 'Test') {
            altClassName = 'Testing'
        }
        try {
            ClassLoader classLoader = Thread.currentThread().contextClassLoader
            String namePrefix = altClassNamePrefix ?: classNamePrefix
            subClassHandle = classLoader.loadClass(
                "${classPackageName}.${namePrefix.capitalize()}${classNameSuffix}"
            )
            Boolean classNameIsSuperClassSubclass = superClass.isAssignableFrom(subClassHandle)
            newNotification = notification ?: new Notification(config: instanceConfig, script: script)
            if (!classNameIsSuperClassSubclass) {
                throw new UnsupportedOperationException(
                    "The provided ${classNameSuffix.toLowerCase()}Type ${classNamePrefix} is not a" +
                        " valid subclass of the ${altClassName.capitalize() ?: classNameSuffix.capitalize()} " +
                        'abstract base class. This is a requirement of created' +
                        " ${altClassName.toLowerCase() ?: classNameSuffix.toLowerCase()} types."
                )
            }
        } catch (all) {
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
            throw new UnsupportedOperationException(
                "Invalid ${altClassName.capitalize() ?: classNameSuffix.capitalize()} Type. " +
                    "Error: ${all.message}"
            )
        }
        Object subClass = subClassHandle.newInstance(
            config: instanceConfig, script: script, notification: newNotification, psc: psc
        )

        subClass as DockerPipelineLib
    }

/**
 * This validates a builds container version matches the regular expression for compatible  version
 * numbers
 *
 * @param buildConfig The config map taken from the closure body in the call method
 * @param buildInstance The Build instance to use to get information for validation
 *
 * @return any issues found.
 */
    List<String> validateBuildConfigContainerVersion(Map<String, Object> buildConfig,
                                                     Build buildInstance) {
        List issues = []
        if (buildConfig?.containerVersion && !(buildConfig?.containerVersion =~ buildInstance.CONTAINER_VERSION_REGEX)) {
            issues.add('Invalid build containerVersion. Regex for containerVersion is ' +
                "${buildInstance.CONTAINER_VERSION_REGEX}"
            )
        }
        issues
    }

    Ticket ticketFromPhase(Map phaseTicket, Notification notification) {
        def ticketInstance = loadInstance(
            Ticket, 'com.cigna.ticket', phaseTicket.ticketType, 'Ticket', phaseTicket, notification)
        ticketInstance.ticketConfiguration = phaseTicket
        phaseTicket['phaseInstance'] = ticketInstance
        ticketInstance.init()
        ticketInstance.prePodConfig()
        return (Ticket) ticketInstance
    }

    Map loadSinglePhase(String phaseLabel, Map<String, Object> phase, Notification notification, List<String> issues) {
        Phase phaseInstance = null

        if (phase?.buildType) {
            // XXX.cnm - needs to cast explicitly to `Build` otherwise when we call `phaseInstance.validate()`
            // it will call Phase.validate(), *not* Build.validate()
            Build buildInstance = loadInstance(
                Build, 'com.cigna.builds', phase.buildType, 'Build', phase, notification) as Build
            phaseInstance = buildInstance
            phase['phaseInstance'] = buildInstance

            issues.addAll(validateBuildConfigContainerVersion(phase, phaseInstance))
        } else if (phase?.ruleengineType) {
            Ruleengine ruleengineInstance = loadInstance(
                Ruleengine, 'com.cigna.ruleengine', phase.ruleengineType, 'Ruleengine', phase, notification) as Ruleengine
            phaseInstance = ruleengineInstance
            phase['phaseInstance'] = ruleengineInstance
        } else if (phase?.deploymentType) {
            Phase ticketInstance = null
            List<Phase> testingInstances
            Deployment deploymentInstance = loadInstance(
                Deployment, 'com.cigna.deployment', phase.deploymentType, 'Deployment', phase, notification)

            if (phase.deploymentType == 'ucd') {
                phase.deployScript = ''
                if (phase.get('rollbackIfFailure', true)) {
                    phase.ucd.rollbackScript = true
                }
                if (phase.postDeployProcessess) {
                    phase.ucd.postDeployScript = true
                }
            }

            if (phase.deploymentType.equalsIgnoreCase('phases')) {
                loadSubPhasesOf(phase, notification)
            }

            deploymentInstance.deploymentConfiguration = phase
            phaseInstance = deploymentInstance
            phase['phaseInstance'] = deploymentInstance

            Map testingInstanceMap = getTestingInstances(phase, false)
            testingInstances = testingInstanceMap.instances as List<Phase>
            testingInstances.each { testingInstance ->
                testingInstance.init()
                testingInstance.prePodConfig()
                issues.addAll(testingInstance.validate())
            }

            if (phase.get('ticket')) {
                // this call also modifies phase.ticket
                ticketInstance = ticketFromPhase(phase.ticket, notification)
                checkAndMaybeUpdateNestedCloudNames(phase.ticket, phase)
                issues.addAll(ticketInstance.validate())
            }

            deploymentInstance.tests = testingInstances
            deploymentInstance.ticket = ticketInstance
        } else {
            Map phaseMap = loadPhase(phase, notification)
            if (phaseMap && phaseMap.instance != null) {
                phaseInstance = phaseMap.instance
                phase['phaseInstance'] = phaseMap.instance
            } else {
                throwInvalidInputException("Unable to determine phase type for phase: $phase")
            }
        }
        phaseInstance.phaseLabel = phaseLabel
        phase.phaseLabel = phaseLabel

        try {
            issues.addAll(phaseInstance.validate())
        } catch (all) {
            issues.add("Unable to determine phase type for phase: ${phase}: ${all.localizedMessage}")
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
        }
        phase
    }

/**
 * This function will dynamically load phases for phases that contain common design patterns.
 *
 * @param phase
 *
 * @return The phase instance and the closure to be assigned to the ['run'] key of the
 * passed phase
 */
    Map<String, Object> loadPhase(Map<String, Object> phase, Notification notification = null) {
        Object pipelineInstance
        String phaseClassString = null

        if (phase?.moduleType) {
            def (Object superClass, String classPackageName, String classNameSuffix) = [Module, 'com.cigna.modules', 'Module']

            pipelineInstance = loadInstance(superClass, classPackageName, '', classNameSuffix, phase, notification)
            phaseClassString = 'module'
            return [instance: pipelineInstance, phaseClosure: {}]
        } else if (phase?.releaseType) {
            def (Object superClass, String classPackageName, String classNameSuffix) = (
                [Release, 'com.cigna.release', 'Release']
            )
            Release releaseInstance = (Release) loadInstance(
                superClass, classPackageName, '', classNameSuffix, phase, notification
            )
            releaseInstance.preValidate()
            pipelineInstance = releaseInstance
            loadSubPhasesOf(phase, notification)

            phaseClassString = 'release'
            return [instance: pipelineInstance, phaseClosure: {}]
        } else if (phase?.parallelType) {
            def (Object superClass, String classPackageName, String classNameSuffix) = (
                [Parallel, 'com.cigna.parallel', 'Parallel']
            )
            pipelineInstance = loadInstance(
                superClass, classPackageName, '', classNameSuffix, phase, notification
            )

            def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhasesFromNamedMap(phase.phases as Map<String, Map<String, Object>>)

            phase.phases = phases
            if (issues.size() > 0) {
                String issuesText = """
            Validation of configuration failed.
            Issues:
            ${issues.join(',\n')}
        """.stripIndent()
                throw new InvalidInputException(issuesText)
            }

            phaseClassString = 'parallel'
            return [instance: pipelineInstance, phaseClosure: {}]
        } else if (phase?.checkpointType) {
            def (Object superClass, String classPackageName, String classNameSuffix) = ([Checkpoint, 'com.cigna.checkpoint', 'Checkpoint'])
            pipelineInstance = loadInstance(
                superClass, classPackageName, '', classNameSuffix, phase, notification
            )

            phaseClassString = 'checkpoint'
            return [instance: pipelineInstance, phaseClosure: {}]
        } else if (phase?.lintingTypes || phase?.freestyleType) {
            def (Object superClass, String classPackageName, String classNameSuffix) = (
                phase?.lintingTypes ?
                    [Linting, 'com.cigna.linting', 'Linting'] : [Freestyle, 'com.cigna.freestyle', 'Freestyle']
            )
            pipelineInstance = loadInstance(
                superClass, classPackageName, '', classNameSuffix, phase, notification
            )

            phaseClassString = phase?.lintingTypes ? 'linting' : 'freestyle'
            return [instance: pipelineInstance, phaseClosure: {}]
        } else if (phase?.testType) {
            Map<String, Object> result = getTestingInstances(['testing': [phase]], true, notification)
            return [instance: result?.instances[0], phaseClosure: {}]
        }
        Object phaseClass
        String phaseClassSubClassString = null
        phase.each { key, value ->
            if (key.endsWith('Type')) {
                phaseClassString = key - 'Type'
                phaseClassSubClassString = value
            }
        }
        if (phaseClassString) {
            String phaseClassBasePath = 'com.cigna.' + (phaseClassString == 'test' ? 'testing' : phaseClassString)
            String phaseClassSuperClassSuffix = phaseClassString.capitalize()
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader()
            try {
                phaseClass = classLoader.loadClass(phaseClassBasePath + '.' +
                    (phaseClassString == 'test' ? 'testing' : phaseClassString).capitalize())
            } catch (all) {
                if (FeatureFlags.showStackTraces) {
                    script.echo(Functions.printThrowable(all))
                }
                throw new UnsupportedOperationException(
                    "Invalid ${phaseClassString.capitalize()} Type. " +
                        "Error: ${all.message}"
                )
            }
            pipelineInstance = loadInstance(
                phaseClass,
                phaseClassBasePath,
                phaseClassSubClassString,
                phaseClassSuperClassSuffix,
                phase,
                notification
            )
            return [instance: pipelineInstance, phaseClosure: { "$phaseClassString"(pipelineInstance) }]
        }
        [:]
    }

    /**
     * takes a phase that has a 'phases' key (that contains a list of subphases), and loads
     * those subphases. Modifies the phase argument, throws if issues are raised by loading
     * the subphases.
     * @param phase a phase that has subphases in the 'phases' key
     * @param notification a notification object to be used in phase instance construction
     */
    private void loadSubPhasesOf(Map<String, Object> phase, Notification notification) {
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(phase.phases, notification)

        // needs to inherit parent cloudName if not already defined
        phases.each { innerPhase ->
            // If the nested phase has a ticketCloud, that takes precedence, otherwise if the parent phase contains
            // a ticketCloudName && the child is a ticket phase, it will inherit the parents ticketCloudName.
            checkAndMaybeUpdateNestedCloudNames(innerPhase, phase)

            if (!innerPhase.containsKey('podGroup')) {
                innerPhase.podGroup = phase.podGroup
            }
        }
        phase.phases = phases
        if (issues.size() > 0) {
            String issuesText = """
            Validation of configuration failed.
            Issues:
            ${issues.join(',\n')}
        """.stripIndent()
            throw new InvalidInputException(issuesText)
        }
    }

    private void checkAndMaybeUpdateNestedCloudNames(Map<String, Object> innerPhase, Map<String, Object> phase) {
        if (innerPhase.containsKey('ticketCloudName')) {
            if (FeatureFlags.verbose) {
                script.echo("Setting cloudName=${innerPhase.ticketCloudName} using nested ticketCloudName")
            }
            innerPhase.cloudName = innerPhase.ticketCloudName
        } else if (phase.containsKey('ticketCloudName') && innerPhase.containsKey('ticketType')) {
            if (FeatureFlags.verbose) {
                script.echo("Setting cloudName=${phase.ticketCloudName} using parent ticketCloudName")
            }
            // if this nested phase is a ticket phase and the parent has a ticketCloudName, we inherit it.
            innerPhase.cloudName = phase.ticketCloudName
        }

        if (!innerPhase.containsKey('cloudName')) {
            if (innerPhase.containsKey('ticketCloudName')) {
                innerPhase.cloudName = innerPhase.ticketCloudName
            } else {
                innerPhase.cloudName = phase.cloudName
            }
        }

        if (FeatureFlags.verbose && innerPhase.containsKey('ticketType')) {
            script.echo("Running embedded ticket phase in cloud '${innerPhase.cloudName}'")
        }
    }

    Map<String, Object> getTestingInstances(
        Map phase, boolean isStandalone = false, Notification notification = null
    ) {
        List instances = []
        if (phase.get('testing')) {
            phase.testing.each { test ->
                Testing testingInstance = loadInstance(
                    Testing, 'com.cigna.testing', test.testType, 'Test', test, notification
                )
                if (test?.typeOverride != null) {
                    if (
                        test.typeOverride.toLowerCase() != 'integration' && test.typeOverride.toLowerCase() != 'performance'
                    ) {
                        echo("Type Override is not one of: 'Performance', 'Integration'.\nContinuing...")
                    } else {
                        testingInstance.testType = test.typeOverride.toLowerCase()
                    }
                }
                testingInstance.testingConfiguration = test
                testingInstance.deploymentConfiguration = phase
                testingInstance.runBeforeDeployment = test?.runBeforeDeployment ?: false
                testingInstance.setIsStandalone(isStandalone)
                test['phaseInstance'] = testingInstance
                instances.add(testingInstance)
            }
        }
        [instances: instances, phase: phase]
    }

    /* Static methods (pure functions related to phase config/loading) */

    /**
     * Check if the phase is enabled by looking for an 'enabled' key correlated with the phase type.
     * This only checks some phase types, and special-cases 'freestyleType' to align with 'lintingEnabled'.
     * @param phase a phase configuration map
     *
     * @return a boolean indicating if the phase should be included
     */
    static boolean validatePhaseEnabled(Map<String, Object> phase) {
        boolean phaseEnabled = true
        if (phase?.buildType) {
            phaseEnabled = phase.get('buildEnabled', true)
        }

        if (phase?.lintingTypes || phase?.freestyleType) {
            phaseEnabled = phase.get('lintingEnabled', true)
        }

        if (phase?.ruleengineType) {
            phaseEnabled = phase.get('ruleengineEnabled', true)
        }

        if (phase?.packagingType) {
            phaseEnabled = phase.get('packagingEnabled', true)
        }

        if (phase?.deploymentType) {
            phaseEnabled = phase.get('deploymentEnabled', true)
        }

        if (phase?.remoteType) {
            phaseEnabled = phase.get('remoteEnabled', true)
        }

        if (phase?.testType) {
            phaseEnabled = phase.get('testEnabled', true)
        }

        if (phase?.ticketType) {
            phaseEnabled = phase.get('ticketEnabled', true)
        }

        phaseEnabled
    }

    /**
     * Because each individual phase config is used to determine what's in that phase's podTemplate, this
     * helper method injects some global config options into every phase in the config. This is so that we can
     * later access that key and value from the individual phase config rather than global config
     *
     * @param phase A phase config map to be enhanced with global config options
     * @param phaseCacheDecision A boolean that tells us if the user provided config enabled phase cache for this run
     * @param parentConfig The top-level config from which various aspects are propagated to the phase
     *
     * @return A phase map with global config options injected
     */
    static Map<String, Object> injectGlobalToPhaseConfig(Map<String, Object> phase, Map parentConfig) {
        if (phase.ticket) {
            injectParentConfig(phase.ticket as Map, parentConfig)
        }

        phase.testing.each { Map test ->
            injectParentConfig(test, parentConfig)
        }

        injectParentConfig(phase, parentConfig)

        if (!phase.containsKey('checkmarxEnabled')) {
            phase.checkmarxEnabled = parentConfig.checkmarxEnabled != null ? parentConfig.checkmarxEnabled : true
        }
        if (!phase.containsKey('sonarEnabled')) {
            phase.sonarEnabled = parentConfig.sonarEnabled != null ? parentConfig.sonarEnabled : true
        }

        if (parentConfig.containsKey('metadataInArgs')) {
            phase.metadataInArgs = phase.get('metadataInArgs', parentConfig.metadataInArgs)
        }

        phase
    }

    /**
     * Add fields from global config that go to every phase, including testing and ticket subphases
     */
    static private void injectParentConfig(Map<String, Object> phase, Map parentConfig) {

        phase.phaseCache = parentConfig.phaseCache
        phase.stashIncludePattern = parentConfig.stashIncludePattern
        phase.stashExcludePattern = parentConfig.stashExcludePattern
        phase.resourceStrategy = parentConfig.resourceStrategy
        phase.resourceScaleFactor = phase.get('resourceScaleFactor', parentConfig.resourceScaleFactor)
        phase.cloudName = phase.get('cloudName', parentConfig.cloudName)
        if (parentConfig.containsKey('groupBy')) {
            phase.groupBy = phase.get('groupBy', parentConfig.groupBy)
        }
    }

    /**
     * Iterate through all the phases (in `config`) and check that the current branch/changed file list
     * matches to any branchPattern and changePattern values given.
     * Returns the list of phases that match the patterns and aren't disabled via [phaseType]Enabled = false,
     * injecting a number of keys from the top-level config.
     *
     * @param script The Jenkins script object
     * @param config The user provided configuration derived from the original closure
     * @param changedFiles The list of changed files to check against the changePattern
     * @param branchName The branch name being built (for checking against branchPattern)
     *
     * @return The phases that match the branch/change patterns and aren't disabled
     */
    static List<Map<String, Object>> phasesThatMatchPattern(
        def script, Map<String, Object> config, List<String> changedFiles = [], String branchName
    ) {
        List phasesToRun = []
        boolean phaseEnabled
        Map parentConfig = [:]

        // TODO: some of these fields might not need to always be injected
        def fields = [
            phaseCache         : false,
            resourceStrategy   : '',
            cloudName          : '',
            checkmarxEnabled   : true,
            sonarEnabled       : true,
            resourceScaleFactor: 4,
            stashIncludePattern: '',
            stashExcludePattern: ''
        ]

        if (!config?.cloudName) {
            throw new UnsupportedOperationException('Please provide a valid cloudName.')
        }

        fields.each { fieldName, defaultValue ->
            parentConfig."$fieldName" = config.get(fieldName, defaultValue)
        }

        def fieldsWithoutDefaults = [
            'metadataInArgs',
            'groupBy'
        ]

        fieldsWithoutDefaults.each { fieldName ->
            if (config.containsKey(fieldName)) {
                parentConfig."$fieldName" = config."$fieldName"
            }
        }

        if (FeatureFlags.verbose) {
            script.echo("Global Config applied to all phases: $parentConfig")
        }

        config?.phases?.each { Map<String, Object> phase ->
            if (Utils.branchMatchesPattern(branchName, phase?.branchPattern)) {
                if (phase?.changePattern) {
                    String changePattern = phase?.changePattern ?: '.*'
                    if (changedFiles.any { file -> file ==~ /${changePattern}/ }) {
                        phaseEnabled = validatePhaseEnabled(phase)
                        if (phaseEnabled) {
                            phasesToRun.add(injectGlobalToPhaseConfig(phase, parentConfig))
                        }
                    } else {
                        script.echo("Did not match changePattern, will not run phase: ${phase}")
                    }
                } else {
                    phaseEnabled = validatePhaseEnabled(phase)
                    if (phaseEnabled) {
                        phasesToRun.add(injectGlobalToPhaseConfig(phase, parentConfig))
                    }
                }
            } else {
                script.echo("Did not match branchPattern, will not run phase: $phase")
            }
        }

        phasesToRun
    }

    static List<Map<String, Object>> getCommonPreflightPhases(def script, Map<String, Object> config) {
        List commonPreflightPhasesToRun = []
        def resourceStrategy = config?.resourceStrategy ?: ''
        if (phasesHasModule(config) && !script.env.CNP_OVERRIDE_COMMON) {
            // each module should be added from repository if not overridden in the Jenkinsfile
            // might be patched to configure resources/ creds
            commonPreflightPhasesToRun = [
                [
                    moduleType            : "common",
                    cloudName             : Utils.cloud(config),
                    resourceStrategy      : resourceStrategy,
                    moduleName            : "cnp-preflight-check-git-settings",
                    branchPattern         : ".*",
                    releaseBranchPattern  : "",
                    sdlcEnvironment       : "dev",
                    isProductionDeployment: false
                ]/*,
            [
                    moduleType            : "common",
                    cloudName             : Utils.cloud(config),
                    resourceStrategy      : resourceStrategy,
                    moduleName            : "cnp-preflight-check-codeowners",
                    branchPattern         : ".*",
                    releaseBranchPattern  : "",
                    sdlcEnvironment       : "dev",
                    isProductionDeployment: false
            ]*/
            ]
        }
        commonPreflightPhasesToRun
    }

    static boolean phasesHasModule(Map<String, Object> config) {
        config?.phases?.any { phase -> phase?.moduleType != null } ?: false
    }

    /**
     * Iterate through all the phases, instantiate an object of the given Phase type, and validate the
     * configuration for each. If there are any validation issues, throw an exception indicating
     * what items failed validation. If no validation errors return the phases to run with the closure
     * to launch per phase attached
     *
     * @param phasesToRun The phases that should be run
     * @param psc The psc to be passed to the PhaseLoader
     * @param notification A notification object to be set on each phase instance
     *
     * @return The list of phase config maps, with each phaseInstance key set to an instance of the Phase subclass
     */
    static List<Map<String, Object>> loadAndValidatePhases(
        def script, List<Map<String, Object>> phasesToRun, PipelineStateContext psc, Notification notification = null) {
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(phasesToRun, notification)

        if (issues) {
            String issuesText = """
            Validation of configuration failed.
            Issues:
            ${issues.join(',\n')}
        """.stripIndent()
            script.echo(issuesText)
            throw new InvalidInputException(issuesText)
        }
        phases
    }

    @NonCPS
    void throwInvalidInputException(String msg) {
        throw new InvalidInputException(msg)
    }
}
