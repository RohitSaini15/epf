package com.cigna.deployment

import com.cigna.base.Phase
import com.cigna.common.compliance.ComplianceValidator
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.testing.Testing
import com.cigna.ticket.ServicenowTicket
import com.cigna.ticket.Ticket
import com.cloudbees.groovy.cps.NonCPS
import hudson.Functions

import static com.cigna.common.utils.Utils.calculateContainerName
/**
 * Abstract base class for all deployments. All methods that can be used across all builds should be
 * included in this class. This also serves to guarantee that there is a deploy method defined for
 * deployment types and a containerTemplates method that define the container templates needed for
 * the deployment work
 */
abstract class Deployment extends Phase {
    def curlContainerImage = 'enterprise-devops/epf-curl'
    def curlContainerVersion = 'latest'
    def curlContainer = [
        name     : calculateContainerName(curlContainerImage, curlContainerVersion),
        image    : "$curlContainerImage:$curlContainerVersion",
        tty      : true,
        command  : com.cigna.common.utils.Utils.defaultSidecarCommand,
        resources: [
            requests: [
                cpu   : '50m',
                memory: '70Mi',
            ],
            limits  : [
                cpu   : '50m',
                memory: '70Mi'
            ]
        ]
    ]

    protected Map<String, Object> reltioConfiguration
    protected Map<String, Object> deploymentConfiguration
    Boolean useRollbackCode = false
    protected String preClean = 'preclean'

    protected List<Testing> tests = []
    protected List<Testing> preDeployTests
    protected Ticket ticket

    List credsList
    List configsList
    String deployStatus
    String deployMessage

    Deployment() {
        baseValidationItems = ['deploymentType', 'sdlcEnvironment']
        groupID = 'deploy'
    }

    @Override
    Boolean prePodConfig() {
        // If a ticketing phase is auto-created, we need a curl container; it is light on resources
        additionalPodConfig.containers += curlContainer
        super.prePodConfig()
    }

    /*
     * Setter for deploymentConfiguration that also sets config, this is used for validation
     */

    void setDeploymentConfiguration(Map deploymentConfiguration) {
        this.config = deploymentConfiguration
        this.deploymentConfiguration = deploymentConfiguration
    }

    /*
    * Setter for reltioConfiguration that also sets config, this is used for validation
    */

    void setReltioConfiguration(Map reltioConfiguration) {
        this.config = reltioConfiguration
        this.reltioConfiguration = reltioConfiguration
    }

    /**
     * Encompasses the steps to take when deploying the given deploy type
     */
    abstract void deploy()

    /**
     * Encompasses steps to take when rolling back the given deploy type
     * Method is empty because default rollback behavior should be nothing
     */
    void rollback() {
    }

    /*
     * Overriding validate to require branch pattern
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        List versionIssues = []
        boolean requireBranchPattern = true

        if (config?.runInAWS && config?.awsFed) {
            additionalValidationItems += [
                [
                    testString: 'aws', customMessage: 'awsFed cannot be used for this phase.'
                ],
            ]
        }

        versionIssues.addAll(super.validate(requireBranchPattern, phase))
        versionIssues
    }

    void run(
        List credsList = [],
        List configsList = []
    ) {
        this.credsList = credsList
        this.configsList = configsList
        Map deploymentConfig = config ?: [:]
        deploymentConfig = (config.deploymentType.toLowerCase() == 'ucd') ? config.get('ucd') : deploymentConfig
        Boolean isProductionDeployment = deploymentConfig?.isProductionDeployment
        if (isProductionDeployment == null) {
            isProductionDeployment = true
        }
        ComplianceValidator instance = psc.complianceValidator
        if (isProductionDeployment) {
            psc.goalsConfig.goalUpsert('Base')
        }
        try {
            ticketAction('Open')
            resolvePreDeployTests()
            runTests(preDeployTests)
            ticket?.readyToDeploy()
            instance.setState(this, 'state', 'Running')
            runDeployment('deploy')
            instance.setState(this, 'state', 'Success')
            updateTicket('deploySuccess', 'Deployment Success')
        } catch (err) {
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(err))
            }
            String msg = "Deployment failed before application could be deployed: ${err.localizedMessage}"
            script.echo(msg)
            if (ticket) {
                updateTicket('deployFailure', msg)
                ticketAction('Closed')
            }
            throw err
        }

        try {
            runTests(tests)
            updateTicket('deploySuccess', 'Deploy completed, testing successful')
        } catch (err) {
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(err))
            }
            script.echo('Caught exception while testing, attempting a rollback.')
            if (useRollbackCode) {
                deployStatus = 'Rollback'
                runDeployment('rollback')
                updateTicket('deployRollback', 'Deployment testing failed, rollback attempted.')
            } else {
                if (deploymentConfig.rollbackScript) {
                    deploymentConfig.deployScript = deploymentConfig.rollbackScript
                    deployStatus = 'Rollback'
                    runDeployment('deploy')
                    updateTicket('deployRollback', 'Deployment testing failed, rollback attempted.')
                } else {
                    script.echo('No rollback path defined, rethrowing exception.')
                    updateTicket('deployFailure', 'Deployment testing failed, no rollback attempted.')
                }
            }
            if (ticket) {
                ticketAction('Closed')
            }
            throw err
        }
        if (deploymentConfig.postDeployScript) {
            deploymentConfig.deployScript = deploymentConfig.postDeployScript
            script.echo('Deploy test successful, running post deployment.')
            try {
                deployStatus = 'PostDeploy'
                runDeployment('deploy')
                updateTicket('deploySuccess', 'Deployment, testing, post deploy succeeded')
            } catch (err) {
                if (FeatureFlags.showStackTraces) {
                    script.echo(Functions.printThrowable(err))
                }
                script.echo('Post deployment failed. Rethrowing error')
                if (ticket) {
                    updateTicket('deployIssue', 'Deployment and testing successful, Post deployment failed.')
                    ticketAction('Closed')
                }
                throw err
            }
        }

        Map<String, Object> adjData = psc.complianceValidator.retrieveAdjudicatorResponse()
        if (adjData && adjData['articles'] && isProductionDeployment) {
            List<Map<String, Object>> arts = adjData['articles']
            for (int i = 0; i < arts.size(); i++) {
                if (arts[i]['name']
                    && arts[i]['name'] == 'Base'
                    && arts[i]['data']
                    && arts[i]['data']['Servicenow']
                    && arts[i]['data']['Servicenow']['normal_change']
                    && arts[i]['data']['Servicenow']['normal_change']['change_request']) {
                    psc.complianceValidator.normalChangeData =
                        arts[i]['data']['Servicenow']['normal_change']['change_request'][0]
                    ServicenowTicket normalChangeTicket = new ServicenowTicket(script: script, config: config, psc: psc)
                    normalChangeTicket.ticketConfiguration = ['cloudName': config?.ticketCloudName ?: Utils.cloud(config)]
                    normalChangeTicket.fullRun(credsList, configsList)
                }
            }
        }

        if (ticket) {
            ticketAction('Closed')
        }
    }

    protected void ticketAction(String action) {
        script.echo("Executing ticket action '$action'")
        if (this.ticket && this.ticket.ticketType == 'standard') {
            if (action == 'Open') {
                psc.complianceValidator.setState(ticket, 'state', 'Running')
            }
            script.stage("$action Ticket") {
                ticket.fullRun(credsList, configsList)
            }
            psc.complianceValidator.setState(ticket, 'state', action)
        }
    }

    protected void updateTicket(String result, String comment) {
        if (this.ticket && this.ticket.ticketType == 'standard') {
            this.ticket.updateState(result)
            ticket.implementationComments = comment
        }
    }

    protected void flow(String action) {
        if (config.deployEnv) {
            deployMessage = config.deployEnv
        } else {
            deployMessage = "$deployStatus to ${config.sdlcEnvironment}"
        }
        script.withCredentials(credsList) {
            script.dir(baseDirectory) {
                moveFiles('begin')
                withPhaseConfigEnv() {
                    script.stage(generateStageName("${deployMessage}")) {
                        "$action"()
                    }
                }
                moveFiles('end')
            }

        }
    }

    protected void resolvePreDeployTests() {
        preDeployTests = []
        preDeployTests.addAll(tests)
        preDeployTests.removeAll { test -> !test.runBeforeDeployment }
        tests.removeAll { test -> test.runBeforeDeployment }
    }

    protected void runTests(List<Testing> testsToRun) {
        if (testsToRun) {
            testsToRun.each { Testing test ->
                test.fullRun(
                    credsList, configsList
                )
            }
        }
    }

    protected void runDeployment(String action) {
        script.configFileProvider(configsList) {
            if (customWorkspace) {
                script.ws(customWorkspace) {
                    flow(action)
                }
            } else {
                flow(action)
            }
        }
    }
}