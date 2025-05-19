package com.cigna.common.kubernetes

import com.cigna.common.exception.ErrorStepException
import com.cigna.common.exception.InvalidInputException
import com.cigna.common.phases.ContainerComparator
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.DockerUri
import com.evernorth.cloudnativebuild.model.BaseDefaults
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import groovy.json.JsonOutput

import static com.cigna.common.utils.Utils.calculateResources
import static com.cigna.common.utils.Utils.mergeByName
import static java.util.regex.Pattern.compile
import static com.cigna.common.utils.Utils.mergeMaps

/**
 * Fluently generate Pod Template JSON to be passed to
 * the Kubernetes Plugin
 */
class PodConfigGenerator implements Serializable {

    public static final String JNLP_CONTAINER_IMAGE = BaseDefaults.podJnlpImage
    public static final String LOGICAL_JNLP_CONTAINER_NAME = 'jnlp'

    public static final String DEFAULT_JNLP_CPU = 500
    public static final String DEFAULT_JNLP_MEMORY = 600
    public static final String DEFAULT_WORKING_DIR = '/home/jenkins/agent'
    public static final boolean DEFAULT_TTY = true

    Map<String, Object> podTemplate = PodTemplateCreator.newPodTemplateCreator(
        tty: DEFAULT_TTY,
        workingDir: DEFAULT_WORKING_DIR,
        jnlpContainerName: LOGICAL_JNLP_CONTAINER_NAME,
        jnlpContainerImage: JNLP_CONTAINER_IMAGE,
        cpu: DEFAULT_JNLP_CPU,
        memory: DEFAULT_JNLP_MEMORY
    ).getTemplate()

    Object script
    final BigDecimal resourceIncrease = 0.5
    final BigDecimal resourceDecrease = 0.49
    final Integer resourceMin = 100

    static final Map<String, Integer> DEFAULT_REQUESTS = [cpu: 500, memory: 2000]

    /**
     * Null-safe (and null-preserving) max of two integers; if either comparand is null, return the other,
     * otherwise return the max of the two non-null integers
     * @param a first integer (or null) to compare
     * @param b second integer (or null) to compare
     * @return
     */
    @NonCPS
    static Integer maxOfNonNulls(Integer a, Integer b) {
        if (a == null) {
            return b
        } else if (b == null) {
            return a
        } else {
            return Math.max(a, b)
        }
    }

    /**
     * Max of two integers; if either comparand is null, return null,
     * otherwise return the max of the two non-null integers
     * @param a first integer (or null) to compare
     * @param b second integer (or null) to compare
     * @return
     */
    @NonCPS
    static Integer maxIfNeitherIsNull(Integer a, Integer b) {
        if (a == null || b == null) {
            return null
        } else {
            return Math.max(a, b)
        }
    }

    @NonCPS
    static long getVersionValue(String originalVersion) {
        // XXX.cnm - Strip the semver label/metadata to avoid parsing errors
        String version = originalVersion.replaceFirst(/-.*/, '')
        if (version.indexOf(".") == -1) {
            return 0
        }
        List<String> versionIndexes = version.tokenize('.')
        long versionSum = 0

        // In order to ensure major.minor.patch don't collide when comparing,
        // allocate 8 bits of bitspace to each version token
        long i = versionIndexes.size()
        if (i != 3) {
            throw new ErrorStepException("Version supplied '$originalVersion' is not a valid semantic version")
        }
        versionIndexes.each {
            versionSum |= Long.valueOf(it) << (16L * (i - 1))
            i--
        }
        return versionSum
    }

    /**
     * Takes two image specs (the first one can be null), and merges them to one image spec that has the 'max'
     * of each attribute. Intended to be the reducing function for creating image specs from module contracts.
     * @param existingSpec the existing image spec from the spec map (can be null if no existing spec)
     * @param newSpec the spec to be folded into the existing spec
     * @return the spec that results from combining the inputs
     */
    @NonCPS
    static Map mergeImageSpecs(Map existingSpec, Map newSpec) {
        if (!existingSpec) {
            return newSpec
        }
        def existingRequest = existingSpec.requests ?: [:] as Map<String, Integer>
        def existingLimits = existingSpec.limits ?: [:] as Map<String, Integer>
        def newRequest = newSpec.requests ?: [:] as Map<String, Integer>
        def newLimits = newSpec.limits ?: [:] as Map<String, Integer>
        return [tag     : (getVersionValue(existingSpec.tag as String) >= getVersionValue(newSpec.tag as String) ? existingSpec.tag : newSpec.tag),
                requests: [cpu             : Math.max(existingRequest.cpu ?: 0, newRequest.cpu ?: 0),
                           memory          : Math.max(existingRequest.memory ?: 0, newRequest.memory ?: 0),
                           ephemeralStorage: Math.max(existingRequest.ephemeralStorage ?: 0, newRequest.ephemeralStorage ?: 0)],
                limits  : [cpu             : maxOfNonNulls(existingLimits.cpu, newLimits.cpu),
                           memory          : maxOfNonNulls(existingLimits.memory, newLimits.memory),
                           ephemeralStorage: maxIfNeitherIsNull(existingLimits.ephemeralStorage, newLimits.ephemeralStorage)]]
    }

    @NonCPS
    static Map modulesToImageSpecs(List<ModuleContract> contracts) {
        contracts.inject([:] as Map<String, Map>) { acc, contract ->
            // split the URI into image and tag because we're going to find the entry by just the image
            Map<String, String> split = DockerUri.imageAndTag(contract.image)
            def newSpec = [tag     : split.tag,
                           // add defaults for any requests not specified in the contract
                           requests: DEFAULT_REQUESTS + contract.requests,
                           limits  : contract.limits]
            acc + [(split.image): mergeImageSpecs(acc[split.image], newSpec)]
        }
    }

    static def addContainersToPodConfig(PipelineStateContext psc, Map additionalPodConfig) {
        return addContainersWithOverrides(psc, null, additionalPodConfig)
    }

    static def addContainersWithOverrides(PipelineStateContext psc, Map maxByContainers, Map additionalPodConfig) {
        List<ModuleContract> optimizedModules = createOptimizedModuleList(psc.globalModuleManager.stateManager(), [])
        if (optimizedModules.isEmpty()) {
            return additionalPodConfig
        }
        def imageSpecs = modulesToImageSpecs(optimizedModules)

        imageSpecs.each { spec ->

            def specMap = spec.value
            def imageName = "${spec.key}:${specMap.tag}"
            def ephemeral = specMap.requests.ephemeralStorage,
                cpu = specMap.requests.cpu,
                memory = specMap.requests.memory
            (ephemeral, cpu, memory) = calculateResources(maxByContainers, imageName, ephemeral, cpu, memory)

            def containerConfig = [
                name      : getContainerName(imageName),
                image     : imageName,
                tty       : DEFAULT_TTY,
                workingDir: DEFAULT_WORKING_DIR,
                command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                resources : [
                    requests: [cpu: "${cpu}m", memory: "${memory}Mi"],
                    limits  : [cpu: "${cpu}m", memory: "${memory}Mi"]
                ]
            ]
            if (ephemeral > 0) {
                containerConfig.resources.requests += ['ephemeral-storage': "${ephemeral}Mi"]
                containerConfig.resources.limits += ['ephemeral-storage': "${ephemeral}Mi"]
            }
            additionalPodConfig.containers += containerConfig

        }
        additionalPodConfig.containers = additionalPodConfig.containers.toUnique(new ContainerComparator())

        return additionalPodConfig
    }


    static List<ModuleContract> createOptimizedModuleList(PipelineStateManager manager, List<StepInvocation> steps) {
        if (manager == null) throw new IllegalArgumentException("PipelineStateManager cannot be null.")
        if (steps == null || steps.isEmpty()) return manager.getContracts()
        HashSet<ModuleContract> optimizedList = []
        steps.each { step ->
            String moduleName = step.options?.filter
            List<ModuleContract> foundContracts = manager.getContracts().findAll { contract -> contract.moduleName == moduleName }
            foundContracts.each { optimizedList.add(it) }
        }
        return optimizedList.toList()
    }

    /**
     * Fluently add and validate containers to the Pod Template
     * @param List of maps representing containers to be added to the Pod Template
     * @return PodConfigGenerator object for Fluent configuration
     */
    PodConfigGenerator addContainers(List<Map> containers, Boolean uniq = false) {
        containers.eachWithIndex { container, index ->
            if (!container?.name) {
                throw new MissingPodTemplatePropertyException(
                    "container.name missing in container ${index}")
            } else if (!container?.image) {
                throw new MissingPodTemplatePropertyException(
                    "container.image missing in container ${container.name} (index: ${index})")
            } else if (!container?.tty) {
                throw new MissingPodTemplatePropertyException(
                    "container.tty missing in container ${container.name} (index: ${index})")
            } else if (!container?.resources?.requests?.cpu) {
                throw new MissingPodTemplatePropertyException(
                    "container.resources.requests.cpu missing in container ${container.name} (index: ${index})")
            } else if (!container?.resources?.requests?.memory) {
                throw new MissingPodTemplatePropertyException(
                    "container.resources.requests.memory missing in container ${container.name} (index: ${index})")
            } else if (!container?.resources?.limits?.cpu) {
                throw new MissingPodTemplatePropertyException(
                    "container.resources.limits.cpu missing in container ${container.name} (index: ${index})")
            } else if (!container?.resources?.limits?.memory) {
                throw new MissingPodTemplatePropertyException(
                    "container.resources.limits.memory missing in container ${container.name} (index: ${index})")
            } else if (container.get('workingDir', DEFAULT_WORKING_DIR) != '/home/jenkins/agent') {
                throw new InvalidPodTemplatePropertyException(
                    "workingDir not set to '${DEFAULT_WORKING_DIR}', "
                        + 'this will break pathing within the script if changed.')
            }
        }

        // Needs to ensure we merge rather than append so that environments and volume mounts etc are provisioned correctly
        podTemplate.spec.containers = mergeByName(podTemplate.spec.containers, containers)

        if (uniq) {
            podTemplate.spec.containers = podTemplate.spec.containers.toUnique(new ContainerComparator())
        }

        this
    }

    /**
     * Fluently add volumes to the Pod Template
     * @param volumes A List of volume objects in map form
     * @return PodConfigGenerator object for Fluent config
     */
    PodConfigGenerator addVolumes(List<Map> volumes) {
        volumes.eachWithIndex { volume, index ->
            if (!volume.name) {
                throw new MissingPodTemplatePropertyException(
                    "volume.name missing in volume ${index}")
            }
        }

        // If we have multiple containers in a single pod using the same sets of volumes, we have to merge them in.
        podTemplate.spec.volumes = mergeByName(podTemplate.spec.volumes, volumes)

        this
    }

    /**
     * Fluently add serviceAccount to the Pod Template
     * @param serviceAccount A String representing the service account used to create the pod
     * @return PodConfigGenerator object for Fluent config
     */
    PodConfigGenerator addServiceAccount(Map<String, Object> phaseConfig, String serviceAccount) {
        boolean aws = phaseConfig?.runInAWS ?: false
        String newServiceAccount = serviceAccount
        if (aws) {
            newServiceAccount = phaseConfig?.aws?.cloudServiceAccountName ?: 'jenkins-robot'
        } else if (!aws && phaseConfig?.aws?.cloudServiceAccountName) {
            throw new InvalidInputException(
                'Unable to apply cloudServiceAccountName as runInAWS was not apart of the phase configuration.'
            )
        }

        // Defect CNPT-1115 - Multi-Cloud package & deploy not being configured with correct service account
        podTemplate.spec.serviceAccountName = newServiceAccount ?: podTemplate.spec.serviceAccountName

        this
    }

    /**
     * Fluently add securityContext to the Pod Template
     * @param context A map representing the securityContext used to create the pod
     * @return PodConfigGenerator object for Fluent config
     */
    PodConfigGenerator addSecurityContext(Map<String, Object> phaseConfig, Map<String, String> securityContext = [:]) {
        def currentSecurityContext = Utils.securityContext(phaseConfig, securityContext)
        if (FeatureFlags.verbose) {
            if (podTemplate.spec.securityContext.size() > 0) {
                script.echo("Merging securityContext '${currentSecurityContext}' in to '${podTemplate.spec.securityContext}'")
            }
        }

        podTemplate.spec.securityContext = mergeMaps(podTemplate.spec.securityContext, currentSecurityContext)

        this
    }

    /**
     * Fluently add minio container to the Pod Template
     * @param phaseConfig A Map representing the user provided phase configuration
     * @return PodConfigGenerator object for Fluent config
     */
    PodConfigGenerator addMinioContainer(Map<String, Object> phaseConfig) {
        if (phaseConfig?.phaseCache && !phaseConfig?.runInAWS) {
            String mcVersion = (phaseConfig?.buildType == 'plz') ? 'plz' : 'latest'
            Map minioContainer = [
                name           : 'mc',
                image          : "enterprise-devops/mc:${mcVersion}",
                imagePullPolicy: 'Always',
                workingDir     : DEFAULT_WORKING_DIR,
                tty            : DEFAULT_TTY,
                command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
                env            : [
                    [
                        name : 'HOME',
                        value: '/tmp'
                    ],
                    [
                        name     : 'ACCESS_KEY',
                        valueFrom: [
                            secretKeyRef: [
                                name: 'mccreds-secret',
                                key : 'access_key'
                            ]
                        ]
                    ],
                    [
                        name     : 'SECRET_KEY',
                        valueFrom: [
                            secretKeyRef: [
                                name: 'mccreds-secret',
                                key : 'secret_key'
                            ]
                        ]
                    ],
                    [
                        name     : 'BUCKET_URL',
                        valueFrom: [
                            configMapKeyRef: [
                                name: 'mccreds-conf',
                                key : 'mccreds-bucketurl'
                            ]
                        ]
                    ],
                    [
                        name     : 'BUCKET_NAME',
                        valueFrom: [
                            configMapKeyRef: [
                                name: 'mccreds-conf',
                                key : 'mccreds-bucketname'
                            ]
                        ]
                    ],
                    [
                        name : 'MC_HOST_minio',
                        value: 'https://$(ACCESS_KEY):$(SECRET_KEY)@$(BUCKET_URL)'
                    ]
                ],
                resources      : [
                    requests: [
                        cpu   : '25m',
                        memory: '25Mi'
                    ],
                    limits  : [
                        cpu   : '1000m',
                        memory: '2000Mi'
                    ],
                ]
            ]

            podTemplate.spec.containers.addAll(minioContainer)
        }

        this
    }

    Map cloneAndMergeTemplate(List<Map> containers) {
        Map clonedTemplate = podTemplate.clone() as Map
        clonedTemplate.spec.containers = mergeByName(podTemplate.spec.containers, containers)
        clonedTemplate
    }

    // XXX.cnm - This is only accurate if the quantity format is Mi.
    // If Ki or Gi are encountered, the totals will be wrong
    String resourceTotals() {
        BigInteger totalCpuRequest = BigInteger.ZERO, totalCpuLimit = BigInteger.ZERO,
                   totalMemoryRequest = BigInteger.ZERO, totalMemoryLimit = BigInteger.ZERO
        this.podTemplate.spec.containers.each { container ->
            String cpuRequest = container.resources.requests.cpu
            String memoryRequest = container.resources.requests.memory
            String cpuLimit = container.resources.limits.cpu
            String memoryLimit = container.resources.limits.memory

            def cpuRequestInt = findIntegerPart(cpuRequest).toBigInteger()
            def cpuLimitInt = findIntegerPart(cpuLimit).toBigInteger()
            def memoryRequestInt = findIntegerPart(memoryRequest).toBigInteger()
            def memoryLimitInt = findIntegerPart(memoryLimit).toBigInteger()

            if (FeatureFlags.debug) {
                script.echo("container ${container.name} contributing ${cpuRequest}:${cpuLimit} cpu and ${memoryRequest}:${memoryLimit} memory")
            }
            totalCpuRequest += cpuRequestInt
            totalCpuLimit += cpuLimitInt
            totalMemoryRequest += memoryRequestInt
            totalMemoryLimit += memoryLimitInt
        }

        """\033[1;34m
            |\t\t******************************************************
            |\t\t               \033[4mPod's Resource Utilization\033[24m
            |
            |\t\t     Total CPU Request:\t\t${totalCpuRequest}m (millicores)
            |\t\t     Total CPU Limit:\t\t${totalCpuLimit}m (millicores)
            |\t\t     Total Memory Request:\t${totalMemoryRequest}Mi (Mebibytes)
            |\t\t     Total Memory Limit:\t${totalMemoryLimit}Mi (Mebibytes)
            |
            |\t\t******************************************************
        \033[0m""".stripMargin()
    }

    public String findIntegerPart(String value) {
        def pattern = compile(/(\d+)(\.\d+)?([a-zA-Z]+)/)

        def matcher = pattern.matcher(value)
        def isMatched = matcher.matches()
        (isMatched ? matcher.group(1) : value)
    }

    PodConfigGenerator assessResources(String strategy = '') {
        if (!strategy) {
            return this
        }
        this.podTemplate.spec.containers.eachWithIndex { container, index ->
            Integer cpuRequestInt = container.resources.requests.cpu.findAll(/[\d]/).join().toInteger()
            Integer cpuLimitInt = container.resources.limits.cpu.findAll(/[\d]/).join().toInteger()
            Integer memoryRequestInt = container.resources.requests.memory.findAll(/[\d]/).join().toInteger()
            Integer memoryLimitInt = container.resources.limits.memory.findAll(/[\d]/).join().toInteger()

            switch (strategy) {
                case 'High Performance':
                    cpuRequestInt += cpuRequestInt * resourceIncrease
                    cpuLimitInt += cpuLimitInt * resourceIncrease
                    memoryRequestInt += memoryRequestInt * resourceIncrease
                    memoryLimitInt += memoryLimitInt * resourceIncrease
                    break
                case 'High Concurrency':
                    cpuRequestInt -= cpuRequestInt * resourceDecrease
                    cpuRequestInt = (cpuRequestInt < resourceMin) ? resourceMin : cpuRequestInt

                    memoryRequestInt -= memoryRequestInt * resourceDecrease
                    memoryRequestInt = (memoryRequestInt < resourceMin) ? resourceMin : memoryRequestInt

                    cpuLimitInt -= cpuLimitInt * resourceDecrease
                    cpuLimitInt = (cpuLimitInt < cpuRequestInt) ? cpuRequestInt : cpuLimitInt

                    memoryLimitInt -= memoryLimitInt * resourceDecrease
                    memoryLimitInt = (memoryLimitInt < memoryRequestInt) ? memoryRequestInt : memoryLimitInt
                    break
            }

            this.podTemplate.spec.containers[index].resources.requests.cpu = "${cpuRequestInt}m"
            this.podTemplate.spec.containers[index].resources.requests.memory = "${memoryRequestInt}Mi"
            this.podTemplate.spec.containers[index].resources.limits.cpu = "${cpuLimitInt}m"
            this.podTemplate.spec.containers[index].resources.limits.memory = "${memoryLimitInt}Mi"
        }

        resourceWarning(strategy)

        this
    }

    void resourceWarning(String strategy) {
        switch (strategy) {
            case 'High Performance':
                script.echo(
                    '''
                    |!!! WARNING !!!
                    |Selecting 'High Performance' mode can result in running out of
                    |quota more often if more than one pipeline is running in a namespace at a time.
                    '''.stripMargin()
                )
                break
            case 'High Concurrency':
                script.echo(
                    '''
                    |!!! WARNING !!!
                    |Selecting 'High Concurrency' mode will most likely result in a slower pipeline and can
                    |cause certain processes to fail by running out of memory or failing to start due to not
                    |having enough memory or cpu.
                    '''.stripMargin()
                )
                break
        }
    }

    /**
     * Serialize the Pod Template object to a Json String
     * @return Json String representing the Pod Template
     */
    String toJson() {
        JsonOutput.toJson(podTemplate)
    }

    /***
     * Creates a container name based on the image name and version
     * @param imageName - the full path to an image
     * @return image name in format imagenamevversion
     */
    @NonCPS
    static String getContainerName(String imageName) {
        if (!imageName) throw new IllegalArgumentException("Module list is missing image information. Please check your build configuration.")
        if (imageName.contains(LOGICAL_JNLP_CONTAINER_NAME)) return LOGICAL_JNLP_CONTAINER_NAME
        return imageName.substring(imageName.lastIndexOf('/') + 1).replace(':', 'v').replace('.', '').replace('_', '').trim()
    }

    def PodConfigGenerator addAutotuningGroup(Map config, String podGroup) {
        // We can't use a static pod label to specify which custom resource autotuning should use to recommend limits
        // each teams project must be able to reproducibly use it's own autotuning resource

        // do not generate pod recommendations for unique pods as they are not reproducible
        if (config.containsKey('groupBy') && config.groupBy == 'unique') {
            podTemplate.metadata.labels.remove('epf_pod_group')
        } else {
            podTemplate.metadata.labels.epf_pod_group = Utils.calculateAutotuningGroupName(script.env.JOB_NAME as String, podGroup)
        }

        this
    }

    PodConfigGenerator addAnnotations() {
        podTemplate.metadata.annotations.epf_branch = script.env.CHANGE_BRANCH ?: script.env.BRANCH_NAME
        this
    }

    class MissingPodTemplatePropertyException extends Exception {
        MissingPodTemplatePropertyException(String message) {
            super(message)
        }
    }

    class InvalidPodTemplatePropertyException extends Exception {
        InvalidPodTemplatePropertyException(String message) {
            super(message)
        }
    }
}
