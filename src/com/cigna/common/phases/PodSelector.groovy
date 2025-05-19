package com.cigna.common.phases

import com.cigna.common.exception.ErrorStepException
import com.cigna.common.kubernetes.NamespaceReportingService
import com.cigna.common.kubernetes.PodAutotuningService
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.kubernetes.PodProfile
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.CheckpointUtils
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS

import java.util.regex.Pattern

import static com.cigna.common.utils.Utils.*

class PodSelector {
    private Map<String, Object> checkoutConfiguration = CommonGit.defaultSCMSettings()
    public List<Map<String, Object>> phaseConfigs = []
    public Map<String, Object> podTemplates = [:]
    public Map<String, String> podLabels = [:]
    public Map<String, String> podClouds = [:]
    public Map<String, PodConfigGenerator> podGenerators = [:]
    public String baseCloudName = ''
    public String pipelineMetadataFileName = ''
    public boolean envInMetadata = ''
    public def jnlpConfig = null
    def script
    CommonGit commonGit = null
    Stack<String> podCloudStack = new Stack<String>()

    @NonCPS
    static PodSelector Instance(Object script = null) {
        new PodSelector(script)
    }

    void accumulate(Map<String, Object> phase) {
        phaseConfigs.add(phase)
        accumulateRepoSettings(phase)

        if (phase.containsKey('testing')) {
            accumulateContainers(phase.testing, 'test')
        }
        if (phase.containsKey('ticket')) {
            accumulateContainers([phase.ticket], 'ticket')
        }
        if (phase.containsKey('phases') && phase.phaseInstance?.accumulateSubphaseContainers) {
            accumulateContainers(phase.phases, 'subphase')
        }
    }

    private void accumulateRepoSettings(Map<String, Object> phase) {
        if (phase.containsKey('repo')) {
            boolean lfs = phase.repo.get('lfs', false)
            boolean shallow = phase.repo.get('shallow', false)
            boolean noTags = phase.repo.get('noTags', false)
            if (lfs) {
                checkoutConfiguration.repo.lfs = true
            }
            if (noTags) {
                checkoutConfiguration.repo.noTags = true
            }
            if (shallow) {
                checkoutConfiguration.repo.shallow = true
                script.echo("checkoutConfiguration.repo.shallow: ${checkoutConfiguration.repo.shallow}")
            }
            // unlikely, but phases may well want to check out different repo urls
            if (phase.repo.containsKey('url')) {
                checkoutConfiguration.repo.urls += phase.repo.url
            }
            // unlikely, but phases may well want to check out different branches
            if (phase.repo.containsKey('branch')) {
                checkoutConfiguration.repo.branches += phase.repo.branch
            }
            if (phase.repo?.doGenerateSubmoduleConfigurations == true) {
                checkoutConfiguration.repo.doGenerateSubmoduleConfigurations = true
            }
        }
    }


    /**
     * XXX.cnm - We only checkout code once per pod, instead of for each phase, so we need to extract any checkout specific configuration from
     *         - each phase definition in order to ensure settings like shallow or lfs are honored.
     *
     *         Additionally, we support a new checkoutCode primitive that lets teams checkout additional repos to specific workspace
     *         locations, allowing more complex scm patterns to be supported
     */
    void checkoutCode(String branch = '', String gitWorkspace = script.env.WORKSPACE) {
        this.commonGit = this.commonGit ?: newCommonGit()
        this.commonGit.checkout(branch, gitWorkspace)
    }

    @NonCPS
    private CommonGit newCommonGit() {
        new CommonGit(checkoutConfiguration, script)
    }

    def select(PipelineStateContext psc, String containerName, String cloudName = baseCloudName, Closure<Object> body) {
        // Need to take account of cloudName - i.e. one pod-template per cloudName
        if (FeatureFlags.verbose) {
            script.echo("Select '$containerName', podGroup: '$cloudName', cloudName '${podClouds[cloudName]}', " +
                "Pod Cloud Stack: ${podCloudStack.toListString()} (head ${podCloudStack.peek()})")
        }

        def output = null
        if (podClouds[cloudName] != podClouds[podCloudStack.peek()]) {
            podCloudStack.push(cloudName)
            // this phase/module needs it's own podTemplate as it is in a different cloud
            script.echo("Starting nested pod for cloud '${podClouds[cloudName]}'($cloudName) using label '${podLabels[cloudName]}', template: '${podTemplates[cloudName]}'")
            if (podGenerators.containsKey(cloudName)) {
                script.ansiColor('xterm') {
                    script.echo(podGenerators[cloudName].resourceTotals())
                }
            }
            script.podTemplate(
                showRawYaml: true,
                label: podLabels[cloudName],
                yaml: podTemplates[cloudName],
                workspaceVolume: script.emptyDirWorkspaceVolume(true),
                cloud: podClouds[cloudName],
                namespace: podClouds[cloudName],
                yamlMergeStrategy: script.override()
            ) {
                script.node(podLabels[cloudName]) {
                    script.echo("Switching to container '$containerName'")
                    output = script.container(containerName) {
                        script.dir(script.env.WORKSPACE) {
                            checkoutCode()
                            body()
                        }
                    }
                }
            }
            podCloudStack.pop()
        } else {
            script.echo("Switching to container '$containerName'")
            output = script.container(containerName) {
                // just in case any previously executed phases have updated the metadata
                preloadEnvironmentForLookups(psc)
                CheckAndUpdateLookupVariables(psc)
                body()
            }
        }

        output
    }


    void start(def config) {
        Reset()
        checkoutConfiguration.githubCredentialsId = config.gitHubCredentialsId ?: config.githubCredentialsId
        // if repo is null, we need to elvis the default values
        checkoutConfiguration.repo.shallow = ifNull(config.repo?.shallow, false)
        checkoutConfiguration.repo.noTags = ifNull(config.repo?.noTags, true)
        script.echo("checkoutConfiguration.repo.shallow: ${checkoutConfiguration.repo.shallow}")
        checkoutConfiguration.suppressWorkspaceSafeDirectory = ifNull(config?.suppressWorkspaceSafeDirectory, true)
        // checkoutConfiguration.phaseCache = false is explicitly set to false so pod checkout always happens, please be aware when changing this boolean
        checkoutConfiguration.phaseCache = false
        baseCloudName = config.cloudName
        pipelineMetadataFileName = config.pipelineMetadataFileName ?: 'disabled'
        envInMetadata = ifNull((boolean) config.envInMetadata, false)
        checkJNLPOverrides(config)
    }

    private def checkJNLPOverrides(def config) {
        if (config.containsKey('container')) {
            jnlpConfig = config.container
        } else if (config.containsKey('containers')) {
            jnlpConfig = (config.containers as List).find { it.name == 'jnlp' }
        } else if (config.containsKey('jnlp')) {
            jnlpConfig = config.jnlp
        }
    }

    @NonCPS
    void throwError(String msg) {
        throw new ErrorStepException(msg)
    }

    void calculatePodTemplates(Map<String, Object> config, List<Map<String, Object>> collectedPhases) {
        start(config)
        collectedPhases.each { phase ->
            if (!phase.containsKey('phaseCache')) {
                phase.phaseCache = config?.phaseCache ?: false
            }
            // if this is an EKS cloudName force runInAWS
            if (isRunningInEKS(phase.cloudName)) {
                phase.runInAWS = true
            }
            phase.phaseInstance.init()
            if (phase.phaseInstance.prePodConfig()) {
                script.echo("Accumulating pod template requirements for ${phase.phaseInstance.displayName()} : ${phase.phaseInstance.containerImage}:${phase.phaseInstance.containerVersion}(${phase.phaseInstance.containerName})")
                accumulate(phase)
            } else {
                throwError("Phase ${phase.phaseName} did not pass prePodConfig - ABORTING")
            }
        }
        finish()
    }

    /**
     * XXX.cnm - TODO - We now need to use a podGroup value to either group by cloudName, or if a podGroup is specified,
     *         - TODO - then we use the podGroup but still need to know what cloud it should run in; this allows us
     *         - TODO - to partition phases into separate pods, even if they're running in the same cloud.
     */
    void finish() {
        // Phases that need all other phases in it's own group to be loaded before it can finish configuring itself
        phaseConfigs.each { phaseConfig ->
            phaseConfig.phaseInstance.postPodConfig()
        }
        // Only apply the sub grouping if we have a checkpoint phase in the phase map
        if (phaseConfigs.any { it -> it.containsKey('checkpointType') }) {
            phaseConfigs = CheckpointUtils.assignPodGroupsByCheckpointGrouping(phaseConfigs)
        }

        phaseConfigs.groupBy {
            cloud(it, baseCloudName)
        }.each { podGroup, configs ->
            /**
             * if a team is using podGroup or groupBy and it results in only synthetic pod group names being used
             * (i.e. managed or group1, group2,etc) we will still have a podGroup based on any openshift namespaces
             * since they are essentially the default groups. It was more complex to update the logic
             * (and more error prone) to remove these zero length pod groups than it was to filter the groups at the
             * point where the code was sensitive to it.
             */
            if (configs?.size() > 0) {
                def maxByContainers = findGroupedContainerResourceMaximums(configs)
                def uniqueContainers = findUniqueContainers(configs)
                def (Object serviceAccountName, Object securityContext) = calculateSecurityContext(configs)

                configs.each { phaseConfig ->
                    def cloudName = phaseConfig.cloudName
                    createPodGenerator(podGroup, cloudName)

                    buildTemplate(phaseConfig, podGroup, serviceAccountName, securityContext, maxByContainers, uniqueContainers)
                }
            }
        }

        // If we have no baseCloud pod, we must inject it; this means all phases run in separate pods
        if (!podGenerators.containsKey(baseCloudName)) {
            createPodGenerator(baseCloudName, baseCloudName)
        }

        // since JNLP containers will be present in every single pod, we allow teams to define their container overrides
        // for jnlp (and only jnlp) at the top level cignaBuildFlow scope but also at phase scope; if at phase scope, it
        // will only apply to the pod that "owns" the phase. If defined at the top level scope, it will be applied to all
        // pods.
        if (jnlpConfig) {
            def memory = extractNumeric(jnlpConfig.memory)
            def cpu = extractNumeric(jnlpConfig.cpu)
            podGenerators.each { key, value ->
                def jnlpContainer =
                    value.podTemplate.spec.containers.find { it.name == 'jnlp' }
                if (jnlpConfig.memory) {
                    jnlpContainer.resources.requests.memory = "${memory}Mi"
                    jnlpContainer.resources.limits.memory = "${memory}Mi"
                }
                if (jnlpConfig.cpu) {
                    jnlpContainer.resources.requests.cpu = "${cpu}m"
                    jnlpContainer.resources.limits.cpu = "${cpu}m"
                }
            }
        }

        Map<String, PodProfile> podProfiles = null
        if (FeatureFlags.podAutotuning.enabled) {
            podProfiles = fetchRecommendationsAndPossiblyUpdatePods() as Map<String, PodProfile>

        }
        podGenerators.each { entry ->
            if (FeatureFlags.podAutotuning.enabled) {
                def podProfile = podProfiles[entry.key]
                // if we don't have a UID it means this pod has not been executing with EPF-Operator active.
                // the execution of this pipeline will result in the JenkinsPod CR instance being created in the namespace
                // and the next time we execute, we will be able to fetch the UID in order to map this pod to the right
                // ownerReference.
                if (podProfile?.uid) {
                    patchOwnerReference(entry.value.podTemplate, podProfile)
                }
            }
            podTemplates[entry.key] = entry.value.toJson()
        }
        script.echo("Pipeline starting with ${phaseConfigs.size()} phase${phaseConfigs.size() == 1 ? '' : 's'}, running in ${podClouds.size()} cloud${podClouds.size() == 1 ? '' : 's'}")
    }

    private void buildTemplate(
        Map<String, Object> phaseConfig,
        String podGroup,
        def serviceAccountName,
        def securityContext,
        def maxByContainers,
        List uniqueContainers) {
        podGenerators[podGroup]
            .addServiceAccount(phaseConfig, serviceAccountName)
            .addSecurityContext(phaseConfig, securityContext)
            .addVolumes(mergeByName(phaseConfig.phaseInstance.basePodConfig.volumes, phaseConfig.phaseInstance.additionalPodConfig.volumes))
            .addContainers(enhanceContainers(maxByContainers, uniqueContainers, phaseConfig), true)
            .addAutotuningGroup(phaseConfig, podGroup)
            .addAnnotations()
            .assessResources(phaseConfig?.resourceStrategy)

    }

    def fetchRecommendationsAndPossiblyUpdatePods() {
        def podProfiles = [:]
        podGenerators.each { podGroup, generator ->
            script.echo("Attempting to fetch pod-autotuning recommendations for pod group '$podGroup' using credential '${podClouds[podGroup]}'")

            if (podClouds.containsKey(podGroup)) {
                script.withCredentials([
                    script.string(
                        credentialsId: podClouds[podGroup],
                        variable: 'oauthToken'
                    )
                ]) {
                    // We need one Auto-tuning service for each underlying cloud
                    PodAutotuningService autotuningService = newPodAutotuningService(podGroup)
                    def podProfile = profileForPod(podGroup, generator, podLabels[podGroup])
                    podProfile = autotuningService.getRecommendedResourcesForPod(script, podProfile)
                    podProfiles[podGroup] = podProfile
                    // if a JenkinsPod CRD instance exists in the namespace, we have it's uid
                    if (podProfile.recommendations.size() > 0) {
                        updatePod(generator, podProfile)
                    }
                }
            } else {
                script.echo("Not fetching VPA creds for pod group '$podGroup' as no such podClouds entry exists (podClouds = $podClouds)")
            }
        }
        podProfiles
    }

    @NonCPS
    private PodAutotuningService newPodAutotuningService(String podGroup) {
        new PodAutotuningService(podClouds[podGroup], script.env.oauthToken)
    }

    @NonCPS
    def PodProfile profileForPod(def podGroup, PodConfigGenerator generator, String label) {
        new PodProfile(
            podName: label,
            autotuningName: calculateAutotuningGroupName(script.env.JOB_NAME, podGroup),
            namespace: podClouds[podGroup]
        )
    }

    private void createPodGenerator(String podGroup, String cloudName) {
        if (!podGenerators.containsKey(podGroup)) {
            podGenerators[podGroup] = newPodGenerator()
            podLabels[podGroup] = randomPodLabel(script.env.JOB_NAME)
            podClouds[podGroup] = cloudName
        }
    }

    private List calculateSecurityContext(def configs) {
        def securityContexts = findFieldInPhaseInstances(configs, 'securityContext').findAll { (it as Map).size() > 0 }
        def serviceAccountNames = findFieldInPhaseInstances(configs, 'serviceAccount')
        def cloudServiceAccountNames = configs.findAll {
            it.runInAWS && it.containsKey('aws')
        }.collect {
            phaseConfig ->
                phaseConfig.aws.cloudServiceAccountName
        }.unique().toList()
        def serviceAccountName = '', securityContext = null

        if (cloudServiceAccountNames.size() > 0) {
            if (cloudServiceAccountNames.size() > 1) {
                script.echo("*** Cannot currently support multiple cloud serviceAccounts in a single pod template ($serviceAccountNames) - ${serviceAccountNames[0]} will be used")
            }
            serviceAccountName = cloudServiceAccountNames[0] ?: 'jenkins-robot'
        } else {
            if (serviceAccountNames.size() > 1) {
                script.echo("*** Cannot currently support multiple serviceAccounts in a single pod template ($serviceAccountNames) - ${serviceAccountNames[0]} will be used")
            }
            if (serviceAccountNames.size() > 0) {
                serviceAccountName = serviceAccountNames[0]
            }
        }
        if (securityContexts.size() > 1) {
            script.echo("*** Cannot currently support multiple securityContext in a single pod template ($securityContexts) - ${securityContexts[0]} will be used")
        }
        if (securityContexts.size() > 0) {
            securityContext = securityContexts[0]
        }
        return [serviceAccountName, securityContext]
    }

    /**
     * Will return a map, keyed by container identifier (image:tag) that contains all of the maximum resource limits for memory and cpu
     * This ensures that if 4 different phases are combined to use the same container, the container will be given the maximum cpu & memory
     * values required by the most resource demanding phase.
     *
     * @return a map of cpu+memory values, keyed by container identifier.
     */
    static def findGroupedContainerResourceMaximums(def phases) {
        def containers = (phases.collect { Map<String, Object> phase ->
            phase.phaseInstance.additionalPodConfig.containers
        } + phases.collect { Map<String, Object> phase ->
            phase.phaseInstance.basePodConfig.containers
        }).flatten()

        return containers.groupBy { container ->
            container.image.toString()
        }.collectEntries { group, maps ->
            def defaultCpu = findDefaultByKey(maps, 'cpu')
            def defaultMemory = findDefaultByKey(maps, 'memory')
            def defaultEphemeral = findDefaultByKey(maps, 'ephemeral-storage', 0)
            [
                group,
                [
                    cpu      : findValueByKey(maps, 'cpu', defaultCpu),
                    memory   : findValueByKey(maps, 'memory', defaultMemory),
                    ephemeral: findValueByKey(maps, 'ephemeral-storage', defaultEphemeral),

                ].subMap(['cpu', 'memory', 'ephemeral'])
            ]
        }
    }

    private static int findValueByKey(maps, String key, def defaultValue) {
        return extractNumeric(maps.findAll {
            it.containsKey('resources')
        }.max {
            extractNumeric(it['resources'].limits[key])
        }?.resources?.limits?."$key" ?: defaultValue)
    }

    private static def findDefaultByKey(def maps, String key, def defaultValue = -1) {
        return maps.max {
            extractNumeric(it?.resources?.limits?."$key")
        }?.resources?.limits?."$key" ?: defaultValue
    }

    @NonCPS
    private PodConfigGenerator newPodGenerator() {
        return new PodConfigGenerator(script: script)
    }

    private List findUniqueContainers(def configs) {
        def list = configs.collect {
            Utils.mergeByName(it.phaseInstance.basePodConfig.containers, it.phaseInstance.additionalPodConfig.containers)
        }.flatten()
        uniqList(list)
    }

    @NonCPS
    List uniqList(def list) {
        list.toUnique(new ContainerComparator())
    }

    private PodSelector(Object script) {
        this.script = script
    }

    /**
     * We now wrap the top level phase loop with the base pod template; if a phase explicitly references a cloudName that
     * is different from the specified base cloudName, it will embed that phases run method inside an additional pod template
     * As a result of this necessary change (because we cannot take a reference to a pod template context or switch between
     * pod templates), it is possible for 2 pods to exist simultaneously, but *only* if the pipeline uses more than 1 cloud (i.e. eks, etc)
     **/
    void podTemplate(PipelineStateContext psc, String podGroup, Closure<Void> body) {
        baseCloudName = podClouds[podGroup]
        script.echo("Creating base pod '${podLabels[podGroup]}' for pod group '$podGroup' on cloud '$baseCloudName'")
        if (FeatureFlags.verbose) {
            script.echo("Pod Template: '${podTemplates[podGroup]}'")
        }
        script.ansiColor('xterm') {
            script.echo(podGenerators[podGroup].resourceTotals())
        }
        podCloudStack.push(podGroup)
        if (FeatureFlags.reportOnNamespace) {
            namespaceReport(script, baseCloudName)
        }
        script.podTemplate(
            showRawYaml: true,
            label: podLabels[podGroup],
            yaml: podTemplates[podGroup],
            workspaceVolume: script.emptyDirWorkspaceVolume(true),
            cloud: baseCloudName,
        ) {
            def metadataEnv = [:]
            script.node(podLabels[podGroup]) {
                script.dir(script.env.WORKSPACE) {
                    script.stage("SCM Checkout") {
                        script.container('jnlp') {
                            checkoutCode()
                        }
                        metadataEnv = LoadPipelineMetadataInPod(psc)
                    }
                    CheckAndUpdateLookupVariables(psc)
                    script.withEnv(metadataEnv.collect { "${it.key}=${it.value}".toString() }) {
                        body()
                    }
                }
            }
        }
        podCloudStack.pop()
    }

    def namespaceReport(def script, def cloudName) {
        script.withCredentials([
            script.string(
                credentialsId: cloudName,
                variable: 'oauthToken'
            )
        ]) {
            def namespaceService =
                NamespaceReportingService.newReportingService(script.env.oauthToken, cloudName)
            namespaceService.printNamespaceResourceSummary(script, cloudName)
        }

    }

    private List calculateResources(Map maxByContainers, Map container, String type, int divisor = 1) {
        if (!container.containsKey('resources')) {
            container.resources = [
                requests: [cpu: "100m", memory: "100Mi"],
                limits  : [cpu: "1000m", memory: "1000Mi"]
            ]
        }
        def containerId = ContainerComparator.discoverContainerDetails(container)
        def ephemeral = container.resources."$type".containsKey('ephemeral-storage') ?
            extractNumeric(container.resources."$type"."ephemeral-storage") : 0
        def cpu = extractNumeric(container.resources."$type".cpu)
        def memory = extractNumeric(container.resources."$type".memory)
        return calculateResources(maxByContainers, "${containerId[0]}:${containerId[1]}", ephemeral, cpu, memory, divisor)
    }

    def curlContainer = [
        [
            name      : 'epf-curlvlatest',
            image     : "enterprise-devops/epf-curl:latest",
            tty       : true,
            workingDir: '/home/jenkins/agent',
            command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
            resources : [
                requests: [
                    cpu   : '25m',
                    memory: '125Mi'
                ],
                limits  : [
                    cpu   : '100m',
                    memory: '250Mi'
                ]
            ]
        ]
    ]

    List<Map> enhanceContainers(def maxByContainers, List<Map<String, Object>> containers, Map config) {
        int divisor = calculateDivisor(config)

        // All pod-groups must contain a curl container, due to the way that nested pods work (for snow tickets)
        if (!containers.any { it.name == 'epf-curlvlatest' }) {
            containers += curlContainer
        }
        containers.collect { container ->
            def (ephemeral, cpu, memory) = calculateResources(maxByContainers, container, 'requests', divisor)
            def (limitEphemeral, limitCpu, limitMemory) = calculateResources(maxByContainers, container, 'limits', 1)
            if (!container.containsKey('resources')) {
                container.resources = [:]
            }
            container.resources = [
                requests: [cpu: "${cpu}m", memory: "${memory}Mi"],
                limits  : [cpu: "${limitCpu}m", memory: "${limitMemory}Mi"]
            ]
            if (ephemeral > 0) {
                container.resources.requests += ['ephemeral-storage': "${ephemeral}Mi"]
                container.resources.limits += ['ephemeral-storage': "${limitEphemeral}Mi"]
            }
            if (!container.containsKey('tty')) {
                container['tty'] = true
            }

            if (!container?.containsKey('imagePullPolicy')) {
                container?.imagePullPolicy = 'Always'
            }
            container
        }
    }

    private int calculateDivisor(Map config) {
        def resourceScaleFactor = config.get('resourceScaleFactor', 4)
        return resourceScaleFactor
    }

    def Reset() {
        phaseConfigs.clear()
        podTemplates.clear()
        podClouds.clear()
        podLabels.clear()
        podGenerators.clear()
        checkoutConfiguration = CommonGit.defaultSCMSettings()
        baseCloudName = null
    }

    List findFieldInPhaseInstances(def configs, String key) {
        configs.findAll {
            it.containsKey('phaseInstance') && it.phaseInstance."$key" != null && !it.phaseInstance."$key".empty
        }.collect {
            it.phaseInstance."$key"
        }.unique().toList()
    }

    void accumulateContainers(List<Map> containers, String type) {
        containers.each { phase ->
            script.echo("---> Accumulating $type container ${phase.phaseInstance.containerName}")
            accumulate(phase)
        }
    }

    // we want to preserve the order of podGroup/cloudName being used in the run phases, so that we don't accidentally
    // nest pods (i.e. the first pod uses a podGroup which has a name different to the cloud it will run in)
    @NonCPS
    List<String> byPodGroup() {
        phaseConfigs.clone().unique {
            cloud(it)
        }.collect { cloud(it) }
    }

    boolean hasMultiplePods() {
        // if it only has a jnlp container, then it does not have any phases
        podGenerators.findAll { _, generator ->
            generator.podTemplate?.spec?.containers?.size() > 1
        }.size() > 1
    }

    boolean RunsWithPrivilegedContainer(def candidate) {
        def podGroup = cloud(candidate)
        def adjacentPhases = phaseConfigs.findAll { cloud(it) == podGroup && it != candidate }

        adjacentPhases.any {
            it.phaseInstance.serviceAccount != null && !it.phaseInstance.serviceAccount.isEmpty()
        }
    }

    def mapToEnv(Map inputMap, String parentKey = '') {
        def envVariables = [:]
        inputMap.each { String key, Object value ->
            if (value instanceof Map) {
                envVariables += mapToEnv(value, "${parentKey}${key.toUpperCase()}_")
            } else {
                envVariables["${parentKey}${key.toUpperCase()}"] = value
            }
        }
        envVariables
    }

    def LoadPipelineMetadataInPod(PipelineStateContext psc) {
        // Give teams the option to disable propagating environment into metadata, to avoid XLR json bloat
        if (ifNull(envInMetadata, false)) {
            preloadEnvironmentForLookups(psc)
        }
        if (pipelineMetadataFileName == 'disabled' || !script.fileExists(pipelineMetadataFileName)) return [:]

        String fileText = script.sh(script: "cat $pipelineMetadataFileName", returnStdout: true)
        Map<String, Object> productDataMap = script.readYaml(text: fileText)

        def envVariables = mapToEnv(productDataMap)
        envVariables.each { key, value ->
            script.env[key] = value

            psc.metadata.put(key, value, pipelineMetadataFileName, Utils.isSensitive(key))
        }

        envVariables
    }


    /**
     * Basic approach:
     *
     * 1.
     */
    def void CheckAndUpdateLookupVariables(PipelineStateContext psc) {
        if (psc.metadata.size() > 0) {
            phaseConfigs = phaseConfigs.collect { phaseConfig ->
                substituteConfigurationLookups(psc, phaseConfig, script)
            }
        }
    }

    String findCurrentCloudName() {
        podCloudStack.size() > 0 ? podCloudStack.peek() : baseCloudName
    }

    def void preloadEnvironmentForLookups(def psc) {
        def envvars = mapToEnv(script.env.getEnvironment())
        envvars.each { key, value ->
            psc.metadata.put(key, value, 'env', isSensitive(key))
        }
    }

    def void updatePod(PodConfigGenerator generator, PodProfile podProfile) {
        generator.podTemplate.spec.containers.each { container ->
            def containerRec = podProfile.recommendations.find { name, recommendation ->
                name == container.name
            }?.value
            if (containerRec) {
                def cpu = calculateWithBounds(containerRec, 'cpu')
                def memory = calculateWithBounds(containerRec, 'memory')
                script.echo("==> ${FeatureFlags.podAutotuning.dryRun ? "(dryRun) " : ""}Autotuning container ${container.name} : [cpu: ${container.resources.requests.cpu}, memory: ${container.resources.requests.memory}] -> [cpu: $cpu, memory: $memory]")

                if (!FeatureFlags.podAutotuning.dryRun && cpu && memory) {
                    container.resources.requests.cpu = cpu
                    container.resources.requests.memory = memory

                    // XXX.cnm - TODO - Still need to pull the upper&lower bounds from the returned JenkinsPod to set limits.
                    // for now, lets be ultra conservative and see how it affects performance
                    container.resources.limits.cpu = cpu
                    container.resources.limits.memory = memory
                }
            }
        }
    }

    def void patchOwnerReference(def podTemplate, PodProfile podProfile) {
        podTemplate.metadata.ownerReferences = [
            [
                apiVersion: 'evernorth.com/v1',
                kind      : 'JenkinsPod',
                name      : podProfile.autotuningName,
                uid       : podProfile.uid,
                controller: true,

            ]
        ]
    }

    //check if target is within upper & lower bounds
    @NonCPS
    def String calculateWithBounds(def containerRec, String field) {
        def (baseNumber, baseSuffix) = extractNumericPart(containerRec.target?."$field")
        def (upperNumber, upperSuffix) = extractNumericPart(containerRec.upperBound?."$field")
        def (lowerNumber, lowerSuffix) = extractNumericPart(containerRec.lowerBound?."$field")
        def scaledBase = scaleBySuffix(baseNumber, baseSuffix, field)
        def scaledUpper = scaleBySuffix(upperNumber, upperSuffix, field)
        def scaledLower = scaleBySuffix(lowerNumber, lowerSuffix, field)

        // scaling normalizes the suffix
        def scaleFactor = field == 'cpu' ? 'm' : 'Mi'
        [scaledBase, scaledUpper, scaledLower].any { it == null } ? null : "${scaledBase < scaledLower ? scaledLower : (scaledBase > scaledUpper ? scaledUpper : scaledBase)}$scaleFactor"
    }

    // normalize to m for cpu, Mi for memory
    @NonCPS
    def scaleBySuffix(BigDecimal number, String suffix, String field) {
        if (!number || !suffix) return null
        def oneK = new BigDecimal(1024.0)
        switch (field) {
            case 'cpu': return suffix == "m" ? number : (number * 1000.0)
            case 'memory': switch (suffix) {
                case 'Ki': return number.divide(oneK)
                case 'Mi': return number
                case 'Gi': return number * oneK
                default: return null
            }
        }
    }

    @NonCPS
    def extractNumericPart(def input) {
        if (!input) return [null, ""]
        def pattern = Pattern.compile("(\\d+(\\.\\d+)?)([a-zA-Z]*)")
        def matcher = pattern.matcher(input)

        if (matcher) {
            def number = matcher.group(1)
            if (!number) {
                throw new IllegalArgumentException("Input string $input is not a valid amount")
            }
            def suffix = matcher.group(3)

            [number.toBigDecimal(), suffix]
        } else {
            [null, ""]
        }
    }
}
