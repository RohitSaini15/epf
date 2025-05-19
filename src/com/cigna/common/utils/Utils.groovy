package com.cigna.common.utils


import com.cigna.base.JenkinsIO
import com.cigna.common.exception.InvalidInputException
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.modules.MetadataEntry
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.cigna.common.phases.ContainerComparator
import com.cigna.common.scm.CommonGit
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS


import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Utils class for collecting common utility methods that don't fit cleanly into other classes
 */
class Utils {
    public static final defaultSidecarCommand = ['sh', '-c', 'trap "exit" TERM; while [ 1 ]; do sleep 60 & wait $!; done;']
    public static final sensitiveKeys = [
        'pwd', 'password', 'user_password', 'token', 'cred', 'secret',
        'apikey', 'accesskey', 'privatekey', 'clientsecret', 'authtoken',
        'authkey', 'credentials', 'passwd', 'auth', 'token_secret', 'api_secret',
        'api_key', 'client_key', 'client_id', 'refresh_token', 'bearer',
        'basicAuth', 'basic_auth', 'basicauth', 'oauth', 'encryption_key',
        'encryption_secret', 'api_token', 'admin_password', 'admin_pwd',
        'admin_passwd', 'service_account_key', 'service_account_password',
        'ssh_private_key', 'ssh_key', '_auth', '_authz'
    ]
    public static final int INDENTATION = 4

    static Object merge(Object lhs, Object rhs) {
        if (lhs instanceof Map) {
            mergeMaps(lhs, rhs)
        } else if (lhs instanceof List) {
            mergeLists(lhs, rhs)
        } else {
            rhs
        }
    }

    /**
     * Cannot use mapOne << mapTwo because we need to merge lists, not
     * overwrite them
     */
    static Map mergeMaps(Map lhs, Map rhs) {
        rhs.each { k, v ->
            lhs[k] = merge(lhs[k], v)
        }
        lhs
    }

    static List mergeLists(List lhs, List rhs) {
        rhs.eachWithIndex { v, i ->
            lhs[i] = merge(lhs[i], v)
        }
        lhs
    }

    static List<Map> mergeByName(List<Map> lhs, List<Map> rhs) {
        rhs.each { item ->
            int idx = findTargetMap(lhs, item['name'])
            lhs[idx] = merge(lhs[idx], item)
        }
        lhs
    }

    static int findTargetMap(List<Map> containers, String name) {
        int idx = containers.findIndexOf { item -> item['name'] == name }
        idx == -1 ? containers.size() : idx
    }

    /**
     * Checks for podGroup or cloudName in the config, if so
     * return the name of that cloud, otherwise return the default cloud config name
     *
     * @param config The user provided configuration
     * @param defaultCloudName The default cloud name to use if neither key is in config
     *
     * @return The cloud name to use for the phase
     */
    @NonCPS
    static String cloud(Map<String, Object> config = [:], String defaultCloudName = 'kubernetes-pipeline') {
        config?.podGroup ?: config?.cloudName ?: defaultCloudName
    }

    /**
     * Check if config calls for running using the aws cloud config, if so
     * return the serviceAccountName required, otherwise return an empty string
     *
     * @param config The user provided configuration
     *
     * @return String serviceAccountName to use for pod spec
     */
    static String serviceAccount(Map<String, Object> config = [:]) {
        boolean aws = config?.runInAWS ?: false
        String serviceAccountName = ''

        if (aws) {
            serviceAccountName = config?.aws?.cloudServiceAccountName ?: 'jenkins-robot'
        } else if (!aws && config?.aws?.cloudServiceAccountName) {
            throw new InvalidInputException('Unable to apply cloudServiceAccountName as ' +
                'runInAWS was not apart of the phase configuration.')
        }

        serviceAccountName
    }

    /**
     * Check if the config calls for running using the aws cloud config, if so
     * return the pod security context required, otherwise return the default security context
     *
     * @param config The user provided configuration
     * @param securityContext The default security context (map) to use (defaults to an empty map)
     *
     * @return Map of pod security context to add to the pod yaml
     */
    static Map<String, Object> securityContext(Map<String, Object> config = [:], def securityContext = [:]) {
        boolean aws = ifNull((Boolean) config?.runInAWS, ifNull((Boolean) config?.runInAws, false))
        def user = ifNull(config?.awsUser, 42531)
        def group = ifNull(config?.awsGroup, null)
        Map<String, Object> awsContext = [
            runAsUser   : user,
            runAsNonRoot: true
        ]
        Map<String, Object> context = (aws) ? awsContext : securityContext

        if (group) {
            context.runAsGroup = group
            context.fsGroup = group
        }

        context
    }

    static String randomPodLabel(String jobName, String prefix = 'epf-') {
        def (String baseFolder, String jobFolder) = extractJobComponents(jobName)
        int max = /*Kubernetes limit*/ 63 - /*hyphen and suffix*/6 - /*UUID and hyphen*/6
        String newJobName = "$prefix$baseFolder-$jobFolder"

        if (newJobName.length() > max) {
            newJobName = newJobName[0..max]
        }

        String label = newJobName + '-' + (UUID.randomUUID().toString()[0..4])

        label.toLowerCase().replaceAll('_', '-')
    }

    @NonCPS
    private static List extractJobComponents(String jobName) {
        List jobSplit = jobName.replaceAll(/[ ()]/, '').split('/')
        String baseFolder = jobSplit[0]
        String jobFolder = jobSplit[-2]
        [baseFolder, jobFolder]
    }

    @NonCPS
    static String calculateRepoPath(String jobName) {
        // capture all the folders of the JOB_NAME, excluding [orchestrator|pilot]-folders, Non-Production, Production
        """releases/${jobName.split('/').findAll {
            !(it in ['orchestrators-folders', 'pilot-folders', 'Non-Production', 'Production'])
        }.join('/')}"""
    }

    static void retrieveJenkinsSecretfiles(Object script, Map<String, Object> config) {
        script.echo("${config.secretFiles}")
        for (int i = 0; i < config?.secretFiles?.size(); i++) {
            script.withCredentials(
                [script.file(credentialsId: config?.secretFiles[i].credentialsId, variable: 'FILE')]
            ) {
                script.sh(
                    "cp ${script.FILE}  ${config?.secretFiles[i].path} " +
                        "&& chmod +r ${config?.secretFiles[i].path}")
            }
        }
    }

    def static extractImageDetails(String s) {
        ['', '']
    }


    static List calculateResources(Map maxByContainers, String imageName, ephemeral, cpu, memory, int divisor = 1) {
        def containerConfig = maxByContainers ? maxByContainers[imageName] : null
        if (containerConfig) {
            if (containerConfig.containsKey('cpu') && containerConfig.cpu) {
                cpu = containerConfig.cpu
            }
            if (containerConfig.containsKey('memory') && containerConfig.memory) {
                memory = containerConfig.memory
            }
            if (containerConfig.containsKey('ephemeral') && containerConfig.ephemeral) {
                ephemeral = containerConfig.ephemeral
            }
        }

        // check for valid default values
        return [
            Math.floorDiv(ephemeral ?: 0, divisor),
            Math.max(Math.floorDiv((cpu ?: 1000), divisor), 1),
            Math.max(Math.floorDiv(memory ?: 2000, divisor), 1)
        ]
    }

    @NonCPS
    static String calculateContainerName(String ci, String cv) {
        PodConfigGenerator.getContainerName("${ci}:${cv}")
    }

    static void checkAndImportProperties(Object script, Map config) {
        if (config.containsKey('envProperties')) {
            config.envProperties.each { envPropFileName ->
                def envProps = script.readProperties(file: envPropFileName)
                envProps.each { envProp ->
                    script.env[envProp.key] = envProp.value
                }
            }
        }
    }

    static boolean isSensitive(String key) {
        key in sensitiveKeys
    }

    static String getCheckmarxProdBranch(Object script, Map<String, Object> cxSettings) {
        Map<String, Object> gitConfig = CommonGit.defaultSCMSettings()
        CommonGit commonGit = new CommonGit(gitConfig, script)

        return cxSettings.CX_PRODUCTION_BRANCH ?: commonGit.getDefaultBranchName()
    }

    static Map substituteConfigurationLookups(PipelineStateContext psc, Map phaseConfig, Object script) {
        phaseConfig.each { key, value ->
            performSubstitution(psc, key, value, script, phaseConfig)
        }

        phaseConfig
    }

    private static Map performSubstitution(PipelineStateContext psc, String key, def value, def script, Map phaseConfig) {
        if (value instanceof Map) {
            phaseConfig[key] = substituteConfigurationLookups(psc, value, script)
        } else if (value instanceof List) {
            phaseConfig[key] = performListSubstitution(psc, value, script)
        } else if (value instanceof String) {
            phaseConfig[key] = substituteString(psc, value, script)
        } // else just leave intact

        phaseConfig
    }

    static String substituteString(PipelineStateContext psc, String str, Object script) {
        (str as String).replaceAll(/lookup\:\{([^{}]+)\}/) { match ->
            def placeholder = match[1]
            psc.metadata.get(placeholder, match[0])
        }
    }

    @NonCPS
    static regionFromEcrUrl(String url) {
        Matcher matcher = (url =~ /^.*\.ecr\.([a-z0-9-]+)\.amazonaws.com$/)
        if (!matcher.matches()) {
            throw new UnsupportedOperationException('Invalid region detected in url: ' + url)
        }
        matcher[0][1]
    }

    // In JDK 10+, we can use -XX:+UseContainerSupport to autoscale according to container resources
    // Both Checkmarx & SonarQube base images use JDK11...
    static def calculateOptimalScannerOptions(def amount) {
        def mx = extractNumeric(amount) * 0.75
        "-Xmx${(int) Math.ceil(mx)}m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 -XX:NativeMemoryTracking=summary"
    }

    static int extractNumeric(def value) {
        if (value instanceof Integer) return value
        if (value == null) return 0
        def p = Pattern.compile(/^(?i)(\d+)(m|g|mi|gi)?$/)
        def m = p.matcher(value)
        if (m.matches()) {
            def multiplier = m.group(2)?.toLowerCase()?.getAt(0) == 'g' ? 1000 : 1
            m.group(1).toInteger() * multiplier
        } else {
            value.toInteger()
        }
    }

    static List performListSubstitution(PipelineStateContext psc, List list, Object script) {
        list.collect { item ->
            if (item instanceof String) {
                substituteString(psc, item, script)
            } else if (item instanceof List) {
                performListSubstitution(psc, item, script)
            } else if (item instanceof Map) {
                substituteConfigurationLookups(psc, item, script)
            } else item
        }
    }

    /**
     * If `candidate` is null, return `replacement`, else `candidate`.
     * Contrast this with the elvis operator, which replaces any false-y value (false, empty string, etc).
     * @param candidate a T that might be null
     * @param nullReplacement a T to return if candidate is null 
     * @return candidate or nullReplacement
     */
    @NonCPS
    static <T> T ifNull(T candidate, T nullReplacement) {
        candidate == null ? nullReplacement : candidate
    }


    static String explainExecutionPlan(PipelineStateContext psc, def groups, def allPhases) {
        def output = "================================================== EXECUTION PLAN ==================================================\n" +
            "Processing pod groups in this order: ${groups}\n"
        output = writeExecutionPlanForPhasesToStr(psc, output, groups, allPhases, null, 0)

        "$output\n===================================================================================================================="
    }

    @NonCPS
    static String writeContainerDetailsToStr(PipelineStateContext psc, def phaseInstance, def activeCloudName, int indent) {
        PodConfigGenerator generator = psc.podSelector.podGenerators[activeCloudName]
        def containers = (
            [generator.podTemplate.spec.containers[0]] +
                phaseInstance.additionalPodConfig.containers + phaseInstance.basePodConfig.containers
        ).toUnique(new ContainerComparator())

        containers.collect {
            "${''.padLeft(indent + INDENTATION)}-> container ${it.name}, cpu: ${it.resources.limits.cpu}, memory: ${it.resources.limits.memory}${it.resources.limits.containsKey('ephemeral') ? ", ephemeral: ${it.resources.limits.ephemeral}" : ''}"
        }.join('\n')
    }

    static String writeExecutionPlanForPhasesToStr(
        PipelineStateContext psc,
        String output,
        def groups,
        def allPhases,
        String msg = null,
        int indent = INDENTATION,
        String defaultCloudName = null
    ) {
        def i = 0
        indent += 4
        output += groups.collect { String entry ->
            def phasesInGroup = allPhases.findAll {
                cloud(it, defaultCloudName) == entry
            }

            def j = 0
            def inner = phasesInGroup.collect { def phase ->
                def activeCloudName = cloud(phase, defaultCloudName)
                def outstr = "${''.padLeft(indent)}  PHASE ${j++} [${phase.phaseInstance.displayName()}]${phase.phaseInstance.serviceAccount != '' ? "(${phase.phaseInstance.serviceAccount})" : ''}(${phase.cloudName}):\n" +
                    "${writeContainerDetailsToStr(psc, phase.phaseInstance, activeCloudName, indent)}"
                outstr = writeChildPhasesToStr(psc, 'phases', phase, outstr, groups, indent)
                outstr = writeChildPhaseToStr(psc, 'ticket', phase, outstr, groups, indent)
                outstr = writeChildPhasesToStr(psc, 'testing', phase, outstr, groups, indent)
                outstr
            }.join("\n")
            if (phasesInGroup.size() > 0) {
                def prefix = "${''.padLeft(indent)}PHASE GROUP ${i++}: $entry # PHASES ${phasesInGroup.size()}\n"
                (msg ? msg : prefix) + inner
            } else {
                ""
            }
        }.findAll { !it.isEmpty() } join("\n")
        output
    }

    private static String writeChildPhasesToStr(PipelineStateContext psc, String fieldName, Map<String, Object> phase, String outstr, groups, int indent) {
        if (phase.containsKey(fieldName) && phase."$fieldName"?.size() > 0) {
            outstr = writeExecutionPlanForPhasesToStr(psc, outstr, groups, phase."$fieldName",
                formatLabel(indent, fieldName), indent + INDENTATION, cloud(phase))
        }
        outstr
    }

    private static String writeChildPhaseToStr(PipelineStateContext psc, String fieldName, Map<String, Object> phase, String outstr, groups, int indent) {
        if (phase.containsKey(fieldName) && phase."$fieldName" != null) {
            outstr = writeExecutionPlanForPhasesToStr(psc, outstr, groups, [phase."$fieldName"],
                formatLabel(indent, fieldName), indent + INDENTATION, cloud(phase))
        }
        outstr
    }

    private static GString formatLabel(int indent, String fieldName) {
        return "\n${''.padLeft(indent + INDENTATION)}-> $fieldName:\n"
    }

    @NonCPS
    static String calculateAutotuningGroupName(String jobName, String podGroup) {
        def maxLength = 63
        def (String baseFolder, String jobFolder) = extractJobComponents(jobName)
        baseFolder = baseFolder in ['orchestrators-folders', 'pilot-folders'] ? '' : "${baseFolder}."

        def dotPodGroup = '.' + (podGroup?.length() > maxLength ?
            podGroup.substring(podGroup.length() - maxLength) : podGroup ?: '')

        // we must preserve the pod-group, so truncate the project name if necessary since
        // pod-group and baseFolder have predictable sizes that won't exceed limits
        if (baseFolder.size() + jobFolder.size() + dotPodGroup.size() >= maxLength) {
            def maxSize = maxLength - baseFolder.size() - dotPodGroup.size()
            jobFolder = jobFolder.take(maxSize)
        }
        "${baseFolder}${jobFolder}${dotPodGroup}".replaceAll('_', '.').replaceFirst(/^\./, '').toLowerCase()
    }

    static String normalizeToMi(String quantity) {
        def kiValue = quantity.replaceAll('Ki', '').toBigDecimal()

        "${kiValue.divide(BigDecimal.valueOf(1024)).setScale(2, BigDecimal.ROUND_HALF_UP)}Mi"
    }
    /**
     * Convenience method for wrapping truthy string values. Takes an Object and a begin and end 
     * string (where end defaults to begin), and returns a String.
     * If the Object is truthy, then wrap its String value in begin and end, otherwise return the empty
     * string.
     * Examples: 
     * - `wrapIfTruthy('abc', '[', ']')` returns '[abc]'
     * - `wrapIfTruthy('', '[', ']')` returns ''
     * @param subject the object to be wrapped if truthy
     * @param begin what to put before a truthy value
     * @param end what to put before a truthy value (defaults to `begin`)
     * @return a wrapped String value or the empty string
     */
    @NonCPS
    static String wrapIfTruthy(Object subject, String begin, String end = begin) {
        return !subject ? '' : "$begin$subject$end"
    }

    /**
     * If the Object passed is truthy, wrap its string value in single quotes
     */
    @NonCPS
    static String sQuote(Object subject) {
        return wrapIfTruthy(subject, "'")
    }

    /**
     * If the Object passed is truthy, wrap its string value in double quotes
     */
    @NonCPS
    static String dQuote(Object subject) {
        return wrapIfTruthy(subject, '"')
    }

    /**
     * Polymorphic alternative for the Iterable version
     * @param s
     * @return
     */
    @NonCPS
    static String joinIfAllPresent(String s) {
        return s ?: ''
    }

    /**
     * If all members of `strs` are truthy (not null or empty), join them with `separator`.
     * Otherwise, return the empty String.
     * @param strs an Iterable of Strings to potentially be joined
     * @param separator a separator for joining `strs` (defaults to a single space)
     * @return `strs` joined w/ `separator` or an empty string
     */
    @NonCPS
    static String joinIfAllPresent(Iterable<String> strs, String separator = ' ') {
        return strs?.every() ? strs.join(separator) : ''
    }


    /**
     * Constructs args/command lines.
     * For each element of `arguments`, if it's a string, use an empty string if null, else the original value.
     * If it's a list/iterable, then join the list if all elements are truthy.
     * Intended usage is something like:
     * buildCommandArgs('maven', ['-someArg', someVar])
     * In this example, if someVar is null or empty, then the `-someArg` is thrown out, so the return value is 'maven'.
     * If someVar is 'abc' (a truthy value), then the result is 'maven -someArg abc'.
     * @param arguments Strings and/or lists of strings to be combined
     */
    @NonCPS
    static String buildCommandArgs(Object... arguments) {
        return arguments.collect { joinIfAllPresent(it) }.findAll().join(' ')
    }

    public static final String EKS_NAME_PATTERN = /.*(-eks-jenkins-cluster-|eks-prod).*/

    static boolean isRunningInEKS(def currentCloud) {
        currentCloud ==~ EKS_NAME_PATTERN
    }

    /**
     * When using shared library in jenkinsfile, jenkins creates environment variables like library.{libraryname}.version
     * library.epf.version to refer the epf library branch used, eg, main
     * As per unix standard, https://pubs.opengroup.org/onlinepubs/7908799/xbd/envvar.html, Environment variable names used by the utilities in the XCU specification
     * consist solely of upper-case letters, digits and the "_" (underscore) from the characters defined in Portable Character Set
     * This method is to update . to _ in the variable name to enable any phase to access this variable as library_epf_version
     * @param key
     * @return key with . replaced with _
     */
    @NonCPS
    static String dotsToUnderscores(String key) {
        key.replace('.', '_')
    }

    /**
     * \u001B: Matches the escape character.
     * \[: Matches the literal [ character.
     * [0-9;]*: Matches any sequence of digits (0-9) and semicolons (;), which are used in ANSI codes to specify different formatting options.
     * m: Matches the literal m character, which marks the end of an ANSI color code.
     * @param text
     */
    static removeAnsiColorCodes(String text) {
        // Define a regex pattern to match ANSI color codes
        def pattern = /\u001B\[[0-9;]*m/
        // Substitute ANSI color codes with an empty string
        def cleanedText = text.replaceAll(pattern, '')
        return cleanedText
    }

    /**
     * Check whether the branch name provided matches the pattern provided. 
     * If the branch name is null or empty, return defaultOutcome (which defaults to true). 
     * If the pattern is null, return defaultOutcome.
     *
     * @param branchName branch name to check
     * @param pattern the pattern to check against
     * @param defaultOutcome a boolean to return if the other params don't facilitate checking
     */
    @NonCPS
    static boolean branchMatchesPattern(String branchName, String pattern, boolean defaultOutcome = true) {
        if (!branchName || pattern == null) {
            return defaultOutcome
        }
        return branchName ==~ pattern
    }


    // NonCPS-ish constructors
    @NonCPS
    static StringBuilder StringBuilder() {
        return new StringBuilder()
    }

    @NonCPS
    private static PipelineStateContext newPSC(def script, Map<String, Object> config = [:]) {
        return new PipelineStateContext(script, config)
    }

    /* `initializeSplunkins` can't be NonCPS'ed, so this lets us initialize the field outside the
     * constructor, without consumers caring about the machinations
     */

    static PipelineStateContext PipelineStateContext(def script, Map<String, Object> config = [:]) {
        def psc = newPSC(script, config)
        psc.splunkEvent = JenkinsIO.initializeSplunkins(script)
        return psc
    }

    @NonCPS
    static MetadataEntry MetadataEntry(String key, Object value, String moduleName = '', boolean isSensitive = false) {
        return new MetadataEntry(key, value, moduleName, isSensitive)
    }

    @NonCPS
    static ModuleContract createModuleContract(
        Map contractParameters
    ) {
        // add logic for any defaults or required fields for modulecontract
        ModuleContract moduleContract = new ModuleContract(contractParameters)
        moduleContract
    }
}
