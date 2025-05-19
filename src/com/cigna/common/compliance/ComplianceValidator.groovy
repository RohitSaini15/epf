package com.cigna.common.compliance

import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.phases.PhaseListUtils
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import groovy.time.TimeCategory
import hudson.Functions
import hudson.console.ModelHyperlinkNote
import org.apache.commons.text.StringEscapeUtils

import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.cigna.common.utils.Utils.serviceAccount

/**
 * This class contains logic to validate a pipeline by checking phases for things such a build type,
 * testing types, and lower deployment phases.
 */

class ComplianceValidator implements Serializable {

    private static final Integer GRID_WIDTH = 170
    private static final Integer DATE_WIDTH = 12 // len(MM/DD/YYYY)+2 padding

    private static final String CPU_REQUEST = '500m'
    private static final String MEMORY_REQUEST = '500Mi'
    private static final String CPU_REQUEST_LIMIT = '1000m'
    private static final String MEMORY_REQUEST_LIMIT = '1000Mi'
    private static final Pattern URLPATTERN =
        Pattern.compile(/(https?:\/\/[-a-zA-Z0-9+&@#\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\/%=~_|])/)

    public static final String PROD_WARN_MSG = 'WARNING: Production deployments disabled due to compliance failure. Please see: https://confluence.sys.cigna.com/display/DvOp/Adjudicator+-+How+to+Get+Started'
    public static final String SPECIFIC_FAIL_MSG = 'For help with specific compliance rule failures please see: https://confluence.sys.cigna.com/display/DvOp/Adjudicator+-+Rule+Definitions'

    static Closure<String> urlTransformer = { Matcher m ->
        ModelHyperlinkNote.encodeTo(m.group(1), GridBuilder.TARGET_VALUE)
    }

    private static final Integer ORG = 2
    private static final Integer INSTANCE = 1
    private static final Integer AUTHOR_BUFFER = 2

    private final transient JsonSlurperClassic jsonSlurper = new JsonSlurperClassic()
    private List<Map<String, Object>> phases
    protected List<Map<String, Object>> news = []
    private final Map<String, Object> config
    private final Object script
    private final String cloudName
    private final String phasesLink = 'Please see https://github.sys.cigna.com/cigna/enterprise-pipeline-framework for phase types.'
    private final String complianceGridName = 'Compliance'
    private final String pipelineGridName = 'Pipeline'

    List<Map<String, String>> complianceCheckMessages = []
    protected AdjudicatorOptions adjudicatorOptions
    protected Map<String, Map<String, String>> state
    public Boolean isPipelineCompliant = true
    String veracodeScanID = ''
    private Boolean epfNonProdEnabled = true
    Boolean isProdDeploy = false
    private Map<String, Object> adjudicatorResponse
    Map<String, Object> normalChangeData
    protected String correlationID
    Boolean checkmarxEnabled = true
    Boolean sonarEnabled = true
    Boolean veracodeEnabled = false

    protected final List<Map> jnlpConfig = [
        [
            name     : 'jnlp',
            resources: [
                requests: [
                    cpu   : CPU_REQUEST,
                    memory: MEMORY_REQUEST
                ],
                limits  : [
                    cpu   : CPU_REQUEST_LIMIT,
                    memory: MEMORY_REQUEST_LIMIT
                ]
            ],
        ]
    ]

    @NonCPS
    static ComplianceValidator Instance(
        Object script = null,
        Map<String, Object> config = [:],
        Map<String, Map<String, String>> state = [:],
        String cloudName = ''
    ) {
        new ComplianceValidator(script, config, state, cloudName)
    }


    static Map<String, Object> noDeploymentContract() {
        [
            'compliance': [
                'status' : false,
                'results': [
                    [
                        'Status' : 'SUCCESS',
                        'Message': 'Pipeline does not implement a Deployment phase... Continuing',
                        'Article': 'CICD',
                    ]
                ]
            ]
        ]
    }

    static Map<String, Object> failedCallContract(Object script, Exception ex) {
        script.echo "Got exception ${ex.localizedMessage}"
        [
            'compliance': [
                'status' : false,
                'results': [
                    [
                        'Status' : 'FAILURE',
                        'Message': "Failed to call Compliance Engine: '${ex.localizedMessage}'",
                        'Article': 'CICD',
                    ]
                ]
            ]
        ]
    }

    void failedAuditCall(Object script, Exception ex) {
        script.echo "Audit call caused exception: ${ex.localizedMessage}"
        this.addNewsArticle("No audit call was completed, error ${ex.localizedMessage}")
    }

    static void retrieveServicenowData(PipelineStateContext psc, Map<String, Object> productDataMap) {
        if (productDataMap['productData'] && productDataMap['productData']['applicationServiceID'] &&
            productDataMap['productData']['businessApplicationID']) {
            psc.goalsConfig.executorUpsert('Servicenow',
                [
                    'ApplicationServiceID' : productDataMap['productData']['applicationServiceID'],
                    'BusinessApplicationID': productDataMap['productData']['businessApplicationID'],
                ],
                true)
        }
    }

    static void retrieveAttestationData(PipelineStateContext psc, Map<String, Object> productDataMap) {
        if (psc.goalsConfig.goalExists('Attestation') && productDataMap['productData'] &&
            productDataMap['productData']['applicationServiceID']) {
            psc.goalsConfig.executorUpsert('Attestation',
                [
                    'application_service_id': productDataMap['productData']['applicationServiceID']
                        .toString().toLowerCase()
                ],
                false)
        }
    }

    static void retrieveSecurityReviewData(PipelineStateContext psc, Map<String, Object> productDataMap) {
        if (productDataMap['productData'] && productDataMap['productData']['securityReviewID']) {
            if (productDataMap['productData']['securityReviewID'].startsWith('RITM')) {
                psc.goalsConfig.executorUpsert('Servicenow',
                    [
                        'TSERequestID': productDataMap['productData']['securityReviewID']
                    ],
                    true)
            } else if (productDataMap['productData']['securityReviewID'].startsWith('ASAQ-')) {
                psc.goalsConfig.executorUpsert('Archer',
                    [
                        'ASAQID': productDataMap['productData']['securityReviewID'].substring(5) as Integer
                    ],
                    true)
            }
        }
    }

    static String transformURLs(String msg) {
        StringBuffer sb = new StringBuffer()
        if (msg) {
            Matcher m = URLPATTERN.matcher(StringEscapeUtils.unescapeHtml4(msg))

            while (m.find()) {
                m.appendReplacement sb, urlTransformer.call(m)
            }
            m.appendTail(sb)
        }

        sb.toString()
    }

    static String printFormatted(String s) {
        String prettyStr
        try {
            prettyStr = JsonOutput.prettyPrint(s)
        } catch (e) {
            prettyStr = "Invalid JSON(${s}): ${e.localizedMessage}"

        }

        prettyStr
    }

    Map<String, Object> retrieveAdjudicatorResponse() {
        adjudicatorResponse
    }

    Boolean isProductionDeployment(List<Map<String, Object>> phasesToRun) {
        for (int i = 0; i < phasesToRun.size(); i++) {
            if ((phasesToRun[i].containsKey('deploymentType'))) {
                isProdDeploy =
                    phasesToRun[i].containsKey('isProductionDeployment') ?
                        phasesToRun[i].get('isProductionDeployment') : true
                if (isProdDeploy) {
                    return true
                }
            }
        }
        false
    }

    /**
     * This method formats the grid of a State grid, a compliance check grid, and an error if supplied.
     * @return x A string containing the output grid.
     */
    String outputFormatted(String message = '') {
        String output = ''
        Integer statementWidth = 98
        Integer negativeOne = -1
        output += GridBuilder.formatMessageGrid(complianceGridName, GRID_WIDTH)
        output += GridBuilder.formatTopicGridWithOptions(
            [
                ['Topic': 'Article'],
                ['Topic': 'Statement', 'Width': statementWidth],
                ['Topic': 'Status']],
            GRID_WIDTH
        )

        output += GridBuilder.formatDataGrid(
            complianceCheckMessages,
            ['Article': negativeOne, 'Message': 120, 'Status': negativeOne],
            GRID_WIDTH
        )
        output += GridBuilder.formatGridBuffer(GRID_WIDTH)
        output += GridBuilder.formatMessageGrid(pipelineGridName, GRID_WIDTH)
        output += GridBuilder.formatTopicGridWithOptions(
            [
                ['Topic': 'Phase'],
                ['Topic': 'State'],
                ['Topic': 'Environment']],
            GRID_WIDTH
        )
        List<Map<String, String>> stateList = []
        state.each { stateList += it.value }
        output += GridBuilder.formatDataGrid(
            stateList,
            ['name', 'state', 'environment'],
            GRID_WIDTH
        )
        if (news) {
            output += GridBuilder.formatGridBuffer(GRID_WIDTH)
            int widestAuthor = calculateWidestAuthor()
            output += GridBuilder.formatTopicGridWithOptions(
                [['Topic': 'Author', 'Width': widestAuthor],
                 ['Topic': 'Date', 'Width': DATE_WIDTH],
                 ['Topic': 'Message']],
                GRID_WIDTH
            )
            output += GridBuilder.formatDataGrid(
                news,
                ['Author': widestAuthor, 'Date': DATE_WIDTH, 'Message': negativeOne],
                GRID_WIDTH
            )
        }
        if (message && message != '') {
            output += GridBuilder.formatGridBuffer(GRID_WIDTH)
            output += GridBuilder.formatMessageGrid(message, GRID_WIDTH)
        }
        output += GridBuilder.formatMessageGrid(phasesLink, GRID_WIDTH)
        output.toString()
    }

    void addNewsArticle(String msg) {
        this.news = this.news ?: []
        this.news += [
            [
                Author : 'Auditor',
                Date   : Date.newInstance().dateString,
                Message: transformURLs(msg),
            ]
        ]
    }

    int calculateWidestAuthor() {
        int maxLen = 0

        news?.each { item ->
            if (item?.containsKey('Author')) {
                int curLen = (int) item?.Author?.size() ?: 0
                if (curLen > maxLen) {
                    maxLen = curLen
                }
            }
        }
        maxLen + AUTHOR_BUFFER
    }
    /**
     * Set state only allows setting state values for an appropriate phase.
     * @param phase The instance of a phase
     * @param key The key of the state to update
     * @param value The value of said key
     */
    void setState(Object phase, String key, String value) {
        def phaseID = phase.toString()
        if (!this.state?.containsKey(phaseID)) {
            this.state[phaseID] = [:]
        }
        this.state[phaseID][key] = value
    }

    boolean getOverriddenFlag(PipelineStateContext psc, String key, boolean defaultValue) {
        Object conduitData
        Boolean phaseOverride

        if (psc.goalsConfig.goalExists('Conduit')) {
            conduitData = psc.goalsConfig.executorGet('Conduit')
        }

        if (conduitData) {
            conduitData.each { Map<String, Object> p ->
                if (p.containsKey('scanOnly')) {
                    if (p.containsKey(key)) {
                        phaseOverride = p[key] as Boolean
                    }
                } else if (p.containsKey('buildType')) {
                    if (p.containsKey(key)) {
                        phaseOverride = p[key] as Boolean
                    }
                }
            }

            if (phaseOverride != null) {
                return phaseOverride
            }

            if (this.config.containsKey(key)) {
                return this.config[key] as Boolean
            }
        }

        defaultValue
    }

    void retrieveSCMData(PipelineStateContext psc) {
        String scmUrl = script.scm.userRemoteConfigs[0].url
        List segments = scmUrl.tokenize('/')
        String gitRepoName = segments[3]
        String repoName = gitRepoName - ('.git')

        Map<String,String> scms = [
            'git.express-scripts.com': 'esigithub',
            'github.sys.cigna.com'   : 'github',
            'github.com'             : 'publicgithub'
        ]

        psc.goalsConfig.executorUpsert('SCM',
            [
                'repo'  : repoName,
                'owner' : segments[ORG],
                'branch': script.scm.branches[0].name,
                'type': scms.get(segments[INSTANCE], 'github')
            ],
            false
        )
    }

    @SuppressWarnings(['Instanceof'])
    Map<String, Object> caseInsensitiveProductData(Map<String, Object> origMap) {
        Map<String, Object> caseInsensitiveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        origMap.each { entry ->
            if (entry instanceof Map) {
                caseInsensitiveMap.put(entry.key, caseInsensitiveProductData(entry.value))
            } else {
                caseInsensitiveMap.put(entry.key, entry.value)
            }
        }
        caseInsensitiveMap
    }

    void parseProductDataFile(PipelineStateContext psc, Map<String, Object> productDataMap) {
        Map<String, Object> ciProductDataMap = caseInsensitiveProductData(productDataMap)

        if (ciProductDataMap['Adjudictor']) { // Note: Check for this specific misspelling
            ciProductDataMap.put('adjudicator', ciProductDataMap['Adjudictor'])
            ciProductDataMap.remove('Adjudictor')
        }

        if (ciProductDataMap['adjudicator'] && ciProductDataMap['adjudicator']['goals']) {
            ciProductDataMap['adjudicator']['goals'].each { g ->
                psc.goalsConfig.goalUpsert(g.toString().toLowerCase().capitalize())
            }
        }

        if (psc.goalsConfig.goalExists('Base')) {
            retrieveServicenowData(psc, ciProductDataMap)
            retrieveSCMData(psc)
            retrieveSecurityReviewData(psc, ciProductDataMap)
        }

        retrieveAttestationData(psc, ciProductDataMap)
    }

    void retrieveMetadataFile(String containerName, PipelineStateContext psc) {
        script.container(containerName) {
            String fileText
            Integer goalsExit = script.sh(script: 'test -f ./[gG]oals.json', returnStatus: true)
            if (!script.sh(script: 'test -f ./.productdata', returnStatus: true)) {
                script.echo('Reading in from .productdata file.')
                fileText = script.sh(script: 'cat .productdata', returnStdout: true)
                Map<String, Object> productDataMap = script.readYaml(
                    text: fileText
                )
                if (productDataMap) {
                    parseProductDataFile(psc, productDataMap)
                }
            } else if (!goalsExit) {
                script.echo('No .productdata file found. Reading in from goals.json file.')
                fileText = script.sh(script: 'cat ./[gG]oals.json', returnStdout: true)
                Map<String, Object> map = jsonSlurper.parseText(fileText)
                psc.goalsConfig.mapMerge(map)
                retrieveSCMData(psc)
            } else {
                script.echo('WARNING: No .productdata file or goals.json file found. Please see: ' +
                    'https://confluence.sys.cigna.com/display/DvOp/Adjudicator+-+How+to+Get+Started#AdjudicatorHowtoGetStarted-Settingupyour.productdatafile')
                this.addNewsArticle('WARNING: No .productdata file or goals.json file found. Please see: ' +
                    'https://confluence.sys.cigna.com/display/DvOp/Adjudicator+-+How+to+Get+Started#AdjudicatorHowtoGetStarted-Settingupyour.productdatafile')
            }
        }
    }

    String findAuditCreds() {
        config?.containsKey('AuditJenkinsCreds') ? config?.AuditJenkinsCreds : 'JENKINS_AUTH_CREDS'
    }

    /**
     * Calls the adjudicator's `audit` endpoint, and adds a news article if the response is non-null.
     * Echos and adds a news article with the exception message if an exception is thrown.
     */
    void audit() {
        try {
            Map<String, Object> auditResponse = callAdjudicatorWithPayload('v2/audit', findAuditCreds(), auditClosure())
            if (auditResponse != null) {
                this.addNewsArticle(auditResponse['msg'])
            }
        } catch (ex) {
            failedAuditCall(script, ex)
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(ex))
            }
        }
    }

    Map<String, Object> evict() {
        String podLabel = Utils.randomPodLabel('evict_' + script.env.JOB_NAME)
        List<Map> containers = []
        PodConfigGenerator podGenerator = new PodConfigGenerator(script: script)
            .addServiceAccount(config, serviceAccount(config))
            .addSecurityContext(config, Utils.securityContext(config))
            .addAutotuningGroup(config, 'compliance')
            .addAnnotations()
            .addContainers(containers)

        Map podTemplate = podGenerator.cloneAndMergeTemplate(jnlpConfig)

        String podTemplateYaml = JsonOutput.toJson(podTemplate)
        // XXX.cnm - TODO - REFACTOR Target

        script.podTemplate(label: podLabel, cloud: Utils.cloud(config), yaml: podTemplateYaml) {
            script.node(podLabel) {
                script.container('jnlp') {
                    script.withCredentials(
                        [
                            script.usernamePassword(
                                credentialsId: 'AdjudicatorAuth',
                                usernameVariable: 'USER',
                                passwordVariable: 'PASS'
                            )
                        ]
                    ) {
                        String contractUrl = adjudicatorOptions.url + "cache/evict?key=${correlationID}"
                        String commandString = 'curl --basic -u "$USER:$PASS" -k -X POST -m 600 --http1.1 ' +
                            '-H \'Accept: */*\' '
                        commandString += contractUrl
                        String res = script.sh(script: commandString, returnStdout: true)
                        script.echo("Eviction response: $res")
                    }
                }
            }
        }
    }

    Map<String, Object> adjudicate(PipelineStateContext psc) {
        callAdjudicatorWithPayload('v2/adjudicate', 'AdjudicatorAuth', adjudicateClosure(psc))
    }

    Map<String, Object> callAdjudicatorWithPayload(String endpoint, String authCreds, Closure generatePayload) {
        Map<String, Object> responseBodyObject = null
        String requestBody
        String podLabel = Utils.randomPodLabel(endpoint + '_' + script.env.JOB_NAME)
        List<Map> containers = []
        PodConfigGenerator podGenerator = new PodConfigGenerator(script: script)
            .addServiceAccount(config, Utils.serviceAccount(config))
            .addSecurityContext(config, Utils.securityContext(config))
            .addAutotuningGroup(config, 'compliance')
            .addAnnotations()
            .addContainers(containers)

        Map podTemplate = podGenerator.cloneAndMergeTemplate(jnlpConfig)

        String podTemplateYaml = JsonOutput.toJson(podTemplate)

        script.podTemplate(label: podLabel, cloud: Utils.cloud(config), yaml: podTemplateYaml) {
            script.node(podLabel) {
                script.container('jnlp') {
                    Map<String, String> scmVars = script.checkout([
                        $class                           : 'GitSCM',
                        branches                         : script?.scm?.branches,
                        extensions                       : script?.scm?.extensions ?: [] +
                            [[$class: 'CloneOption', shallow: true]],
                        doGenerateSubmoduleConfigurations: false,
                        submoduleCfg                     : [],
                        userRemoteConfigs                : script?.scm?.userRemoteConfigs,
                    ])
                    if (correlationID == null) {
                        String correlationStrategy = (config?.correlationStrategy ?: 'default').toLowerCase()
                        Map<String, CorrelationStrategy> strategies = [
                            default     : new DefaultStrategy(),
                            git_commit  : new GitCommitStrategy(script, config, scmVars, isProductionDeployment(phases)),
                            user_defined: new UserDefinedStrategy(config),
                        ]
                        correlationID = strategies[correlationStrategy].getCorrelationID() ?:
                            new DefaultStrategy().getCorrelationID()
                    }

                    if (FeatureFlags.debug) {
                        script.echo("Using correlationID: $correlationID")
                    }
                    def responseFilePath = Files.createTempFile('compliance-validator-response-', '.json').toAbsolutePath().toString()

                    String commandString = 'curl --basic -u "$USER:$PASS" '
                    def path = responseFilePath.toString()
                    commandString += "-o ${path} -w '%{http_code}' "
                    commandString += "-b 'Correlation-ID=${correlationID}' "
                    commandString += '-k -X POST -m 600 --http1.1 '
                    commandString += '-H \'Content-Type: application/json\' '
                    commandString += '-H \'Accept: application/json\' '
                    requestBody = generatePayload.call(scmVars)
                    if (FeatureFlags.verbose) {
                        script.echo("RequestBody: $requestBody")
                    }
                    String responseBody
                    def complianceRequestFile = Files.createTempFile('compliance-request-body-', '.json').toAbsolutePath().toString()
                    script.sh("printf '%s' '${requestBody}' > ${complianceRequestFile}")
                    String contractUrl = adjudicatorOptions.url + endpoint
                    commandString += "-d @${complianceRequestFile} "
                    commandString += "'${contractUrl}'"
                    String responseCode = ''
                    if (FeatureFlags.verbose) {
                        script.echo("Command String: $commandString")
                    }
                    script.withCredentials(
                            [
                                    script.usernamePassword(
                                            credentialsId: authCreds,
                                            usernameVariable: 'USER',
                                            passwordVariable: 'PASS'
                                    )
                            ]
                    ) {
                        responseCode = script.sh(script: commandString, returnStdout: true)
                        responseBody = script.sh(script: "cat ${path}", returnStdout: true)
                        script.echo("Made call to Adjudicator. Response Code: $responseCode")
                    }

                    if (FeatureFlags.debug) {
                        script.echo(
                                """|Response Body: 
                                            |${printFormatted(responseBody)}""".stripMargin()
                        )
                    }
                    if (responseBody) {
                        try {
                            responseBodyObject = jsonSlurper.parseText(responseBody)
                        } catch (all) {
                            script.echo(
                                    "Error parsing response. Response: \n ${responseBody}: ${all.localizedMessage}")
                            if (FeatureFlags.showStackTraces) {
                                script.echo(Functions.printThrowable(all))
                            }
                        }
                    }
                    if (responseCode?.toInteger() != 200) {
                        throw new FailedAdjudicatorRequestException(
                                "Compliance Validator returned status $responseCode"
                        )
                    }
                }
            }
        }
        responseBodyObject
    }

    List<Map<String, Object>> retrieveCompliantPhases(PipelineStateContext psc, List<Map<String, Object>> phases, boolean isProdDeploy) {
        List<Map<String, Object>> prodDeployPhases = []
        Boolean isDeployment = false
        initState(phases)

        for (Integer i = 0; i < phases.size(); i++) {
            if (!(phases[i].containsKey('deploymentType'))) {
                continue
            }
            isDeployment = true
            isProdDeploy =
                phases[i].containsKey('isProductionDeployment') ?
                    phases[i].get('isProductionDeployment') : true
            if (phases[i].get('testing')) {
                phases[i].testing.each { testingPhase ->
                    testingPhase['testType'] = testingPhase.phaseInstance.testType
                }
            }
            phases[i]['index'] = i
            if (isProdDeploy) {
                prodDeployPhases += phases[i]
            }
        }
        if (!isDeployment) {
            Map<String, Object> contract = noDeploymentContract()
            this.complianceCheckMessages = contract?.compliance?.results ?: []
            this.isPipelineCompliant = true
            return phases
        }

        psc.goalsConfig.goalUpsert('Conduit')
        psc.goalsConfig.executorUpsert('Conduit', PhaseListUtils.sanitizeForJson(phases))

        Object conduitData = psc.goalsConfig.executorGet('Conduit')
        checkmarxEnabled = getOverriddenFlag(psc, 'checkmarxEnabled', true)
        veracodeEnabled = getOverriddenFlag(psc, 'veracodeEnabled', false)
        sonarEnabled = getOverriddenFlag(psc, 'sonarEnabled', true)

        if (config?.servicenowEnvironment) {
            String snowEnv = config?.get('servicenowEnvironment', 'prod')
            psc.goalsConfig.executorUpsert('Servicenow',
                [
                    'ServicenowEnv': snowEnv
                ],
                true
            )
        }

        conduitData.each { phase ->
            if (phase['deploymentType'] && phase['isProductionDeployment'] == true) {
                psc.goalsConfig.goalUpsert('Base')
                if (phase.containsKey('ticket') == false) {
                    this.addNewsArticle('No standard change template provided in ticket phase so automatically ' +
                        'checking for approved normal change in Servicenow during compliance check.')
                    epfNonProdEnabled = false
                    psc.goalsConfig.goalUpsert('Changemanagement')
                    psc.goalsConfig.executorUpsert('Servicenow',
                        [
                            'NormalChangeEnabled': true,
                        ],
                        true
                    )
                } else {
                    if (phase['ticket']['title'] && phase['ticket']['title'].contains('CHG')) {
                        epfNonProdEnabled = false
                        psc.goalsConfig.goalUpsert('Changemanagement')
                        psc.goalsConfig.executorUpsert('Servicenow',
                            [
                                'NormalChangeEnabled': true,
                                'NormalChangeID'     : phase['ticket']['title'],
                            ],
                            true
                        )
                    }
                }
            }
        }

        if (isProdDeploy || FeatureFlags.nonProdComplianceChecks) {
            try {
                adjudicatorResponse = adjudicate(psc)
            } catch (ex) {
                adjudicatorResponse = failedCallContract(script, ex)
                if (FeatureFlags.showStackTraces) {
                    script.echo(Functions.printThrowable(ex))
                }

            }
            this.complianceCheckMessages = adjudicatorResponse?.compliance?.results ?: []

            this.news += adjudicatorResponse?.News ?: []

            Boolean isCompliant = checkViolations(adjudicatorResponse)
            if (!isCompliant) {
                prodDeployPhases.each {
                    phases.remove(it)
                }
            }

        } else {
            this.complianceCheckMessages = []
            script.echo("Not checking non-prod compliance posture (FeatureFlag.nonProdComplianceChecks:${FeatureFlags.nonProdComplianceChecks})")
        }
        phases
    }

    Boolean checkViolations(Map<String, Object> contract) {
        if (contract['compliance']['status'] == false) {
            isPipelineCompliant = false
        }
        contract['compliance']['status']
    }

    @NonCPS
    void attachMetadata(Map<String, String> scmVars, PipelineStateContext psc) {
        use(TimeCategory) {
            int retentionCount = config?.compliance?.audit?.buildRetention?.buildCount ?: 10
            String minimumBuildDate = config?.compliance?.audit?.buildRetention?.minimumBuildDate
                ?: 1.year.ago.toInstant().truncatedTo(ChronoUnit.MILLIS).toString()
            Map<String, Object> meta = [
                build_name           : script.env.JOB_NAME,
                build_number         : script.env.BUILD_NUMBER,
                epf_version          : script.env['library.epf.version'],
                jenkins_version      : '_',
                build_url            : script.env.BUILD_URL,
                start_time           : Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(),
                git_commit           : scmVars?.GIT_COMMIT ?: 'Unknown',
                git_url              : scmVars?.GIT_URL ?: 'Unknown',
                minimum_build_date   : minimumBuildDate,
                build_retention_count: retentionCount,
                CorrelationID        : correlationID,
                ReferenceID          : script.env.BUILD_TAG,
                prodDeploy           : isProdDeploy,
                FeatureFlags         : [
                    captureActivity  : true,
                    correlatedAudit  : true,
                    epfNonProdEnabled: epfNonProdEnabled,
                    adjudicatePhase  : 'prebuild',
                    debug            : FeatureFlags.debug,
                    trace            : FeatureFlags.verbose
                ]
            ]

            psc.goalsConfig.attach('Metadata', meta)
        }
    }

    /**
     * Loads the phases into compliance Validator. Also modifies the passed-in phases list.
     * 
     * @param phases The list of phases to load and assign to this object.
     */
    ComplianceValidator loadPhases(List<Map<String, Object>> phases) {
        this.phases = phases
        // the default logic is to set stashEnabled to true if it is not otherwise overridden, which means
        // we have to find any phases that have the stash explicitly disabled, in which case, we disable
        // it for all phases. For consistency, if one phase has it disabled, or it is disabled at global
        // scope, it will be disabled for all phases.
        boolean isStashDisabled = config?.containsKey('isStashEnabled') ? !config.isStashEnabled : false

        isStashDisabled = isStashDisabled ?
            true : phases.any { it.containsKey('isStashEnabled') && !it.isStashEnabled }
        if (isStashDisabled) {
            // propagate to global scope if it was defined at phase scope
            config.isStashEnabled = false
        }
        this.phases.eachWithIndex { phase, idx ->
            Object isProdDeploy
            phase.isStashEnabled = !isStashDisabled
            if (phase.containsKey('groupID')) {
                phase.phaseInstance.groupID = phase.groupID
            }

            if (phase.keySet().contains('deploymentType')) {
                if (phase.get('ticket')) {
                    // allow the pod grouping feature to detect when ticket should be included in the parent phases group
                    if (phase.containsKey('groupBy')) {
                        if (phase.cloudName == phase.ticket.cloudName) {
                            phase.ticket.put('parentPhaseGroupID', phase.phaseInstance.groupID)
                        } else {
                            phase.ticket.put('parentPhaseGroupID', 'nested')
                        }
                    }
                }
                if (phase.get('testing')) {
                    phase.testing.each { testingPhase ->
                        testingPhase['testType'] = testingPhase.phaseInstance.testType
                        // allow the pod grouping feature to detect when ticket should be included in the parent phases group
                        if (phase.containsKey('groupBy')) {
                            testingPhase.put('parentPhaseGroupID', phase.phaseInstance.groupID)
                        }
                    }
                }
                isProdDeploy = phase.get('isProductionDeployment')
                if (isProdDeploy) {
                    phase['index'] = idx
                } else if (isProdDeploy == null) {
                    String warningMessage = 'No isProductionDeployment flag for:\n' +
                        "Deployment: ${phase.deploymentType}\n" +
                        "Environment: ${phase.sdlcEnvironment}\n" +
                        'Assuming true, if this is false, please set in phase config.'
                    script.echo(warningMessage)
                    phase['index'] = idx
                    phase['isProductionDeployment'] = true
                }
            }
        }
        this
    }

    protected Closure<String> auditClosure() {
        { scmVars ->
            """
            {"Metadata":{"CorrelationID":"${correlationID}"},"BuildURL":"${script.env.BUILD_URL}"}
            """.toString()
        }
    }

    protected Closure<String> adjudicateClosure(PipelineStateContext psc) {
        { scmVars ->
            retrieveMetadataFile('jnlp', psc)
            attachMetadata(scmVars, psc)
            script.echo(psc.goalsConfig.toString())

            String requestBody = psc.goalsConfig.toJson()

            requestBody
        }
    }

    /*
     * This initializes the state of the compliance validator given the phase list attached to it.
     * @param phases An optional list of phases who's state to initialize. Required for Testing and Ticket phases
     */

    private String printSimpleName(String prefix, Map<String, Object> phase) {
        phase.phaseInstance.displayName(prefix)
    }

    protected void initState(List<Map<String, Object>> phases = null, String prefix = '', Boolean isRelease = false) {

        phases.each { phase ->
            def phaseID = phase.phaseInstance.toString()
            this.state[phaseID] = [:]
            this.state[phaseID]['state'] = isRelease ? 'Deferred' : 'Pending'
            this.state[phaseID]['environment'] = phase.get('sdlcEnvironment', 'Undefined')
            this.state[phaseID]['name'] = printSimpleName(prefix, phase)

            if (phase.keySet().contains('deploymentType')) {
                if (phase.get('testing')) {
                    initState(phase.testing)
                }
                if (phase.get('ticket')) {
                    initState([phase.ticket])
                }
            }
            if (phase.containsKey('phases')) {
                initState(phase.phases, '+--', phase.keySet().contains('releaseType'))
            }
        }
    }

    /*
     * The constructor for the ComplianceValidator object.
     * @param script The script this is running within. This gives us access to steps.
     * @param state An optional state object in case you would like to instantiate it in the middle
     * of a pipeline.
     */

    private ComplianceValidator(
        Object script,
        Map<String, Object> config = [:],
        Map<String, Map<String, String>> state = [:],
        String cloudName = ''
    ) {
        this.script = script
        this.state = state
        this.cloudName = cloudName
        this.config = config
        this.adjudicatorOptions = new AdjudicatorOptions(this.config)
    }
}

class AdjudicatorOptions implements Serializable {
    protected String url
    private final Map<String, String> urls = [
        'prod'   : 'https://adjudicator.cigna.com/',
        'nonprod': 'https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/',
    ]

    AdjudicatorOptions(Map<String, Object> config) {
        if (!config?.adjudicatorEnvironment) {
            config.adjudicatorEnvironment = 'prod'
        }
        this.url = config.adjudicatorEnvironment?.toLowerCase() == 'prod' ?
            this.urls['prod'] : config.adjudicatorEnvironment?.toLowerCase() == 'nonprod' ?
            this.urls['nonprod'] : config.adjudicatorEnvironment
    }
}

class FailedAdjudicatorRequestException extends Exception {
    FailedAdjudicatorRequestException(String message) {
        super(message)
    }
}
