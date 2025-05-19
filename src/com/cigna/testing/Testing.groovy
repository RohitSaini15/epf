package com.cigna.testing

import com.cigna.base.Phase
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import hudson.Functions

/**
 * Abstract base class that defines the contract for testing strategies
 */
abstract class Testing extends Phase {
    Testing() {
        baseValidationItems = ['testType']
        groupID = 'test'
    }
    protected String testType
    protected Map<String, Object> testingConfiguration
    protected Map<String, Object> deploymentConfiguration
    protected String customWorkspace
    protected List credsList
    protected Boolean isTestStandalone = false

    Boolean runBeforeDeployment = false

    /*
     * Sets the config for both testing config and the phase config for validation reasons
     *
     * @param testingConfiguration The configuration to assign to both config and testingConfiguration
     */

    Boolean getIsStandalone() {
        isTestStandalone
    }

    void setIsStandalone(Boolean isStandalone) {
        this.isTestStandalone = isStandalone
    }

    void setTestingConfiguration(Map testingConfiguration) {
        this.config = testingConfiguration
        this.testingConfiguration = testingConfiguration
    }

    /*
     * Overriding validate to include runInAWS validation for testing phases
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        if (config?.runInAWS && config?.awsFed) {
            additionalValidationItems += [
                [testString: 'aws', customMessage: 'awsFed cannot be used for this phase.'],
            ]
        }

        List validationIssues = super.validate(requiresBranchPattern, phase)
        validationIssues
    }

    void run(List credsList = [], List configsList = []) {
        def isStandalone = this.getIsStandalone()
        script.echo("${getClass().name} : isStandalone: ${isStandalone}")

        if (isStandalone) {
            fullRun(credsList, configsList)
        } else {
            runImpl()
        }
    }

    @SuppressWarnings(['UnnecessaryGetter', 'NestedBlockDepth'])
    void fullRun(
        List credsList = [],
        List configsList = []
    ) {
        this.credsList = credsList
        psc.complianceValidator.setState(this, 'state', 'Running')
        try {
            psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
                script.configFileProvider(configsList) {
                    if (customWorkspace) {
                        script.ws(customWorkspace) {
                            testFlow()
                        }
                    } else {
                        testFlow()
                    }
                }
            }
        } catch (e) {
            psc.complianceValidator.setState(this, 'state', 'Failed')
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(e))
            }

            throw e
        } finally {
            if (!isTestStandalone) {
                sendTailoredEmail()
            }
        }
        psc.complianceValidator.setState(this, 'state', 'Success')
    }

    /**
     * Encompasses the steps to take when running the given test type
     */
    abstract void runImpl()

    protected void testFlow() {
        String stage = testingConfiguration?.stage ?: 'Running ' + testingConfiguration.testType + ' Tests'
        script.withCredentials(credsList) {
            moveFiles('begin')
            script.dir(baseDirectory) {
                script.stage(generateStageName(stage)) {
                    this.runImpl()
                }
            }
            moveFiles('end')
        }
    }
}
