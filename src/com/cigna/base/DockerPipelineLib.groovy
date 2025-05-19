package com.cigna.base

import com.cigna.common.exception.ErrorStepException
import com.cigna.common.exception.InconsistentConfigurationException
import com.cigna.common.exception.InvalidInputException
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS

import static com.cigna.common.utils.Utils.mergeByName

/**
 * Base class for all stages
 */
abstract class DockerPipelineLib extends CignaBuildFlowPipelineLib {
    protected String podTemplateContainerName = ''
    protected Map basePodConfig = [volumes: [], containers: []]
    protected Map additionalPodConfig = [volumes: [], containers: []]
    protected String serviceAccount = ''
    protected Map<String, String> securityContext = [:]
    protected String containerName = ''
    protected String groupID = 'support'

    protected String containerImage = ''
    protected String containerVersion = ''
    protected String containerMemory = '500Mi'
    protected String containerCpu = '500m'
    public static int maxContainerMemory = 18000
    public static int maxContainerCpu = 5000
    // The kubernetes documentation indicates that Always will not pull an image if the digest has not changed
    // so setting containerImagePullPolicy to Always will be the most efficient way to ensure images tagged with
    // 'latest'
    protected String containerImagePullPolicy = 'Always'
    public List<Object> baseValidationItems = []
    public List<Object> additionalValidationItems = []

    // If a phase (for example a phase that wraps a module, such as the release/preRelease phases)
    // needs to configure themselves before validation occurs, they can override this method and it
    // is guaranteed to be called before validate() on those modules
    @NonCPS
    void preValidate() {
    }

    List findContainerByName(String name) {
        String configToUpdate = ''
        Integer indexToUpdate = -1

        if (!name) {
            return [indexToUpdate, configToUpdate]
        }
        String mappedName = psc.nameRegistry.nameFor(name)

        additionalPodConfig.containers.eachWithIndex { container, index ->
            if (container.name == mappedName || container.name == name) {
                configToUpdate = 'additionalPodConfig'
                indexToUpdate = index
                return [indexToUpdate, configToUpdate]
            }
        }
        if (indexToUpdate == -1) {
            basePodConfig.containers.eachWithIndex { activeContainer, idx ->
                if (activeContainer.name == mappedName || activeContainer.name == name) {
                    configToUpdate = 'basePodConfig'
                    indexToUpdate = idx
                    return [indexToUpdate, configToUpdate]
                }
            }
        }


        [indexToUpdate, configToUpdate]
    }

    void updateContainerName(String originalName, String newName, String configToUpdate, Integer containerIndex) {
        if (!originalName) {
            return
        }
        Map<String, Object> containerToUpdate = this."$configToUpdate"['containers'][containerIndex]
        String nameOfContainer = containerToUpdate['name']

        this."$configToUpdate"['containers'][containerIndex]['name'] = newName

        if (nameOfContainer == originalName) {
            this.containerName = newName
        }
    }
    /**
     * Custom setter to allow config to override the containerImage in the additionalPodConfig.
     * Using setContainerImage was not working
     *
     * @param containerImage The container image name to use for the build
     */
    void updateContainerImage(String updatedImage, String configToUpdate, Integer containerIndex) {
        if (!updatedImage) {
            return
        }
        Map<String, Object> containerToUpdate = this."$configToUpdate"['containers'][containerIndex]
        String nameOfContainer = containerToUpdate['name']
        String version = "${containerToUpdate?.image}".replaceAll(/.*\:/, '')

        this."$configToUpdate"['containers'][containerIndex]['image'] = "${updatedImage}:${version}"

        if (nameOfContainer == this.containerName) {
            this.containerImage = updatedImage
        }
    }

    /**
     * Custom setter to allow config to override the containerVersion in the additionalPodConfig
     * Using setCContainerVersion was not working
     *
     * @param containerVersion The container version name to use for the build
     */
    void updateContainerVersion(String updatedVersion, String configToUpdate, Integer containerIndex) {
        if (!updatedVersion) {
            return
        }
        Map<String, Object> containerToUpdate = this."$configToUpdate"['containers'][containerIndex]
        String nameOfContainer = containerToUpdate['name']
        String image = "${containerToUpdate?.image}".replaceAll(/:.*/, '')

        this."$configToUpdate"['containers'][containerIndex]['image'] = "${image}:${updatedVersion}"

        if (nameOfContainer == this.containerName) {
            this.containerVersion = updatedVersion
        }
    }

    /**
     * Custom setter to allow config to override the containerMemory in the additionalPodConfig
     *
     * @param containerMemory The amount of memory to assign to the container for the build
     */
    void updateContainerMemory(Integer updatedMemory, String configToUpdate, Integer containerIndex) {
        if (!updatedMemory) {
            return
        }
        String nameOfContainer = this."$configToUpdate"['containers'][containerIndex]['name']

        if (updatedMemory > maxContainerMemory) {
            script.echo("You have attempted to set the container memory to ${updatedMemory}, which is above this phase's maximum." +
                " The maximum, ${maxContainerMemory}Mi, will be used.")
            this."$configToUpdate"['containers'][containerIndex]['resources']['limits']['memory'] =
                "${maxContainerMemory}Mi"
        } else {
            this."$configToUpdate"['containers'][containerIndex]['resources']['limits']['memory'] =
                "${updatedMemory}Mi"
        }

        if (nameOfContainer == this.containerName) {
            this.containerMemory = "${updatedMemory}Mi"
        }
    }

    /**
     * Custom setter to allow config to override the containerCpu in the additionalPodConfig
     *
     * @param containerCpu The amount of memory to assign to the container for the build
     */
    void updateContainerCpu(Integer updatedCpu, String configToUpdate, Integer containerIndex) {
        if (!updatedCpu) {
            return
        }
        String nameOfContainer = this."$configToUpdate"['containers'][containerIndex]['name']

        if (updatedCpu > maxContainerCpu) {
            script.echo("You have attempted to set the containers cpu to ${updatedCpu}, which is above this phase's maximum." +
                "The maximum, ${maxContainerCpu}m, will be used.")
            this."$configToUpdate"['containers'][containerIndex]['resources']['limits']['cpu'] =
                "${maxContainerCpu}m"
        } else {
            this."$configToUpdate"['containers'][containerIndex]['resources']['limits']['cpu'] =
                "${updatedCpu}m"
        }

        if (nameOfContainer == this.containerName) {
            this.containerCpu = "${updatedCpu}m"
        }
    }

    /**
     * Custom setter to allow config to override the phase's default container's imagePullPolicy in
     * the additionalPodConfig
     *
     * @param policy The String value to assign to the default phase's container's imagePullPolicy
     */
    void updateContainerImagePullPolicy(String updatedPolicy, String configToUpdate, Integer containerIndex) {
        if (!updatedPolicy) {
            return
        }

        def allowedPolicies = ['Always', 'IfNotPresent', 'Never']
        if (!(updatedPolicy in allowedPolicies)) {
            throw newPolicyException(updatedPolicy, allowedPolicies)
        }

        String nameOfContainer = this."$configToUpdate"['containers'][containerIndex]['name']

        this."$configToUpdate"['containers'][containerIndex]['imagePullPolicy'] = updatedPolicy

        if (nameOfContainer == this.containerName) {
            this.containerImagePullPolicy = updatedPolicy
        }
    }

    private InvalidInputException newPolicyException(String updatedPolicy, allowedPolicies) {
        return new InvalidInputException("The imagePullPolicy '$updatedPolicy' is not a valid policy ${allowedPolicies}")
    }

    /**
     * Update phase's default container configuration
     *
     * @param config The user provided map of this phase's containerConfig
     */
    void updatePhaseContainer(Map<String, Object> containerConfig = [:]) {
        if (containerConfig) {
            Integer cpu = containerConfig?.cpu ?: 0
            Integer memory = containerConfig?.memory ?: 0
            String name = containerConfig?.name ?: this.containerName
            Integer indexToUpdate
            String configToUpdate

            (indexToUpdate, configToUpdate) = findContainerByName(name)

            if (indexToUpdate >= 0) {
                if (containerConfig.containsKey('image') && containerConfig['image']?.contains(':')) {
                    if (containerConfig.containsKey('version')) {
                        throw throwInconsistentException(containerConfig)
                    }

                    def (img, ver) = containerConfig['image'].split(':')
                    containerConfig?.image = img
                    containerConfig.version = ver
                }
                updateContainerImage(containerConfig?.image, configToUpdate, indexToUpdate)
                updateContainerVersion(containerConfig?.version, configToUpdate, indexToUpdate)
                updateContainerCpu(cpu, configToUpdate, indexToUpdate)
                updateContainerMemory(memory, configToUpdate, indexToUpdate)
                updateContainerImagePullPolicy(containerConfig?.imagePullPolicy, configToUpdate, indexToUpdate)
                if (containerConfig.image || containerConfig.version) {
                    def newName = PodConfigGenerator.getContainerName("${containerConfig.image}:${containerConfig.version}")
                    updateContainerName(containerName, newName, configToUpdate, indexToUpdate)
                    if (indexToUpdate == 0 && configToUpdate == 'additionalPodConfig') {
                        podTemplateContainerName = newName
                        containerName = newName
                    }
                }

            }
        }
    }


    @NonCPS
    private InconsistentConfigurationException throwInconsistentException(Map<String, Object> containerConfig) {
        return new InconsistentConfigurationException(
            "container.image '${containerConfig.image}' and container.version '${containerConfig.version}'" +
                " cannot both contain a version tag")
    }

    protected void updateContainers(List<Map<String, Object>> containers = []) {
        if (containers) {
            containers.each { container ->
                updatePhaseContainer(container)
            }
        }
    }

    /**
     * PHASE API METHOD: This init() method can be used to initialize the phase *before* it is running inside the podTemplate
     * This happens before the pod config is generated so is useful to provide defaults values that weren't set in the
     * constructor but that need to be known before the pod config is generated. You can't call pipeline methods inside
     * init().
     */
    void init() {
        if (!containerName) {
            containerName = PodConfigGenerator.getContainerName("${containerImage}:${containerVersion}")
        }
    }

    /**
     * PHASE API METHOD: If a phase needs all other phases in its own group to be discovered before it can make a
     * decision regarding which pod group it should be scheduled in, can use this method with the guarantee that all
     * of it's adjacent phases will be known.
     */
    Boolean postPodConfig() {
        if (config.containsKey('groupBy')) {
            calculatePodGroup(config.groupBy)
        } else {
            checkForChildPodGroups()
        }
        // Safety Measure if origname or logicalName

        if (config.spec?.containers?.containsKey('origname')) {
            script.echo("Someone coding ${config.spec.containers.get('origname')} NAUGHTILY left origname without CLEANING up...")
            config.spec.containers.remove('origname')
        }

        if (config.spec?.containers?.containsKey('logicalName')) {
            script.echo("Someone coding ${config.spec.containers.get('logicalName')} NAUGHTILY left logicalName without CLEANING up...")
            config.spec.containers.remove('logicalName')
        }

        /**
         * Defect CNPT-948
         *
         * We have a mutually exclusive security constraint that causes the maven build to fail if it is running
         * in the same pod as a container that requires a service account. Due to SCC restricted-csi-v2, we are
         * not able to access cloudbees managed files that were injected in to the workspace due to file permissions
         *
         * To mitigate this, we are forcing the maven build phase (and potentially any phase/module that requires access
         * to managed files) to run in a separate podGroup so that it runs under the correct SCC policy.
         */
        if (!config.containsKey('podGroup')) {
            if (psc.podSelector.RunsWithPrivilegedContainer(config) && needsManagedFileAccess()) {
                script.echo("Phase requires access to managed files and is running in a privileged container - " +
                    "moving to 'managed' pod group")
                config.podGroup = 'managed'
            }
        }

        true
    }

    boolean needsManagedFileAccess() {
        config.containsKey('extraConfigs') && (serviceAccount == null || serviceAccount.isEmpty())
    }

    /**
     * PHASE API METHOD: If a Phase needs to configure its containers dynamically, before the main pod template is created,
     * it can execute this configuration here in prePodConfig()
     * @return
     */
    Boolean prePodConfig() {
        configureSecurityContext()

        updatePhaseContainer(config?.container)
        updateContainers(config?.containers)

        if (config.containsKey('phaseInstance') && !config?.phaseInstance?.containerImage?.isEmpty() && !config?.phaseInstance?.containerVersion?.isEmpty()) {
            podTemplateContainerName = PodConfigGenerator.getContainerName("${config?.phaseInstance?.containerImage}:${config?.phaseInstance?.containerVersion}")
            config.phaseInstance.containerName = podTemplateContainerName
        }

        true
    }

    void loadEnvProperties() {
        Utils.checkAndImportProperties(script, config)
    }

    @NonCPS
    void throwException(String msg) {
        throw new IllegalAccessException(msg)
    }


    @NonCPS
    void throwErrorStepException(String msg) {
        throw new ErrorStepException(msg)
    }

    void configureSecurityContext() {
        if (config?.runAsUser == 'root' || config?.runAsUser == '0') {
            throwException('Using root as runAsUser is not allowed')
        }

        if (config.runInAWS && config?.aws?.cloudServiceAccountName) {
            this.serviceAccount = config.aws.cloudServiceAccountName
        } else {
            this.serviceAccount = config?.serviceAccount ?: this.serviceAccount
        }
        if (config?.containsKey('runAsUser')) {
            this.securityContext = [runAsUser: config.runAsUser]
        }

        if (this.serviceAccount && !this.serviceAccount.isEmpty()) {
            script.env.CNP_POD_SERVICE_ACCOUNT = this.serviceAccount
            if (this.securityContext == null || this.securityContext.isEmpty()) {
                this.securityContext = [:]
            }
        }
        // Until we get per-container service account ability via SCC, disable
        /* if (this.serviceAccount && !this.serviceAccount.isEmpty()) {
             // For now, we apply the service account to all pods sidecar-ed to the phase requesting it,
             // but not other containers, owned by different phases, in the same pod template.
             addSecurityConfiguration('additionalPodConfig', serviceAccount)
             addSecurityConfiguration('basePodConfig', serviceAccount)
         } */
    }

    /*
    void addSecurityConfiguration(String key, String serviceAccount) {
        if (this."$key" && this."$key".containers && this."$key".containers.size > 0) {
            for (i in 0..(this."$key".containers.size() - 1)) {
                this."$key".containers[i].serviceAccountName = this.serviceAccount
                this."$key".containers[i].securityContext = this.securityContext ?: [:]
            }
        }
    }
    */

    /**
     * Combines the basePodConfig and any additionalPodConfig with PodConfigGenerator to
     * return a string representation of the pod config to use for this class.
     *
     * @param serviceAccount a String to set the serviceAccountName to in the resulting pod
     *        Yaml, defaults to an empty string
     * @param securityContext a Map to set the securityContext in the resulting pod Yaml, defaults to empty map
     *
     * @return A string representation of the pod config to use for this class.
     */
    String getPodConfig(
        Map<String, String> securityContext = [:],
        Map<String, Object> phaseConfig = [:]
    ) {
        new PodConfigGenerator(script: script)
            .addServiceAccount(phaseConfig, '')
            .addSecurityContext(phaseConfig, securityContext)
            .addMinioContainer(phaseConfig)
            .addVolumes(mergeByName(basePodConfig.volumes, additionalPodConfig.volumes))
            .addContainers(mergeByName(basePodConfig.containers, additionalPodConfig.containers))
            .assessResources(config?.resourceStrategy)
            .toJson()
    }

    /**
     * Child classes should override this method to provide a better, more meaningful name
     * @return
     */
    def displayName(def prefix = '') {
        getClass().simpleName.split('(?<=[a-z])(?=[A-Z])').join(' ')
    }

    void calculatePodGroup(String groupStrategy) {
        switch (groupStrategy) {
            default:
                script.echo("groupBy value '$groupStrategy' is not a supported strategy ['phase-type', 'unique'], defaulting to 'phase-type'")
                groupStrategy = 'phase-type'
                // DROPTHRU - if unsupported groupStrategy is configured then we default it to 'phase-type' & requires to continue
            case 'phase-type':
                // If we are actually embedded inside another phase, we must inherit the parent groupID so that we
                // don't inadvertently spin up another pod
                if (config.containsKey('parentPhaseGroupID')) {
                    groupID = config.parentPhaseGroupID
                }
                config.podGroup = groupID
                break
            case 'unique':
                if (!config.containsKey('parentPhaseGroupID')) {
                    config.podGroup = UUID.randomUUID().toString()[0..7]
                    checkForChildPodGroups()
                }
                if ((config.containsKey('parentPhaseGroupID')) && (config.parentPhaseGroupID == 'nested')) {
                    config.podGroup = UUID.randomUUID().toString()[0..7]
                }
                break

        }
        if (FeatureFlags.verbose) {
            script.echo("Grouping Phase '${displayName()}' by '$groupStrategy' in group '${config.podGroup}'")
        }
    }


    @NonCPS
    Map updateAllLookupEntries(Map<String, Object> map, String keyType = 'lookup') {
        map.each { key, value ->
            if (value instanceof String && value?.contains("$keyType:") == true) {
                map[key] = resolveLookups(value, keyType)
            } else if (value instanceof Map && (value as Map).size() > 0) {
                map[key] = updateAllLookupEntries(value, keyType)
            }
        }
        map
    }

    @NonCPS
    String resolveLookups(String value, String keyType = 'lookup') {
        if (value.startsWith("$keyType:")) {
            "${psc.metadata.get(value.substring(7))}"
        } else {
            value.replaceAll(/$keyType:\{(\w+)}/) { match ->
                def key = match[1]
                psc.metadata.get(key)
            }
        }
    }

    void checkForChildPodGroups() {
        ['testing', 'phases'].each { grouping ->
            config."$grouping"?.each {
                if (!it.containsKey('podGroup')) {
                    it.podGroup = config.podGroup
                }
            }
        }
        if (config.containsKey('ticket') && config.ticket) {
            if (!config.ticket.containsKey('podGroup')) {
                config.ticket.podGroup = config.podGroup
            }
        }
    }

    static void updateHardLimits(String key, double hardLimit) {
        if (key == 'limits.cpu') {
            maxContainerCpu = hardLimit.toInteger() * 1000
        } else if (key == 'limits.memory') {
            maxContainerMemory = hardLimit.toInteger()
        }
    }
}
