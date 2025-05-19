package com.cigna.testing

import com.cigna.base.Phase
import com.cigna.common.phases.PodSelector
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.Utils
import com.cigna.common.utils.QEATestingUtils
import com.cloudbees.groovy.cps.NonCPS
/**
 * Defines testing steps for Python BDD tests.
 */
class QEABehaveTest extends Testing { 
    String installFolder	
    QEABehaveTest() {
          containerName = 'qea-behave'
          testType = 'integration'
          additionalValidationItems = ['commandString','build','frameworkType']
          containerImage = 'qea-coreops/python-bdd'
          containerVersion = 'latest'
    }
	
    String reportDir
    String reportFile
	
    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            100,
            1000,
            1000,
            1000,
            env
        )

        additionalPodConfig = [
          'volumes'   : [],
          'containers': [containerTemplate.getContainer(containerName)]
      ]

      super.prePodConfig()
    }

    @Override
    void runImpl() {
        psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
            reportDir = testingConfiguration.reportDir
            reportFile = testingConfiguration.reportFile
            if (testingConfiguration.git) {
                QEATestingUtils.checkoutDependencies(script, testingConfiguration)
            }
            runPythonTests()
            config.email = config.email ?: QEATestingUtils.generateEmailConfig(script, testingConfiguration)
            if (testingConfiguration.junit) {
                QEATestingUtils.publishJunitReportRun(script, testingConfiguration)
            }
            if (testingConfiguration.webex) {
                QEATestingUtils.sendWebexNotifications(script, testingConfiguration, "Execution Completed", testingConfiguration.webex.attachmentRequired)
            }
            if (testingConfiguration.smb) {
                QEATestingUtils.smbFileTransfer(script, testingConfiguration)
            }
        }
    }



    /*
     * Run Tests with appropriate arguments
     */
    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'NestedBlockDepth', 'MethodSize'])
    protected void runPythonTests() {
        String commandStr = testingConfiguration.commandString
        String optionalParams = testingConfiguration.optionalParams
        String commandExec = ''
        script.echo('Running Python Tests')     
	    
        if (testingConfiguration.webex) {
            QEATestingUtils.sendWebexNotifications(script, testingConfiguration, 'Execution Started', false)
        }
        if (testingConfiguration.prepCommands) {
            QEATestingUtils.runCommands(script,'prep',testingConfiguration)    
        }

        if (testingConfiguration.installRequirements) {
            script.sh('pip3 install -r requirements.txt --user')
        }
	    
        if (optionalParams != null && !(optionalParams.isEmpty())) {
            commandExec = "${commandStr} ${optionalParams}"
        } else {
            commandExec = "${commandStr}"
        }

        if (testingConfiguration.timeout && testingConfiguration.units) {
            script.timeout(time: testingConfiguration.timeout, unit: testingConfiguration.units) {
                try {			   
                    script.sh(commandExec)
                } catch (all) {
                    script.currentBuild.result = 'FAILURE'                   
                    if (reportDir) {
                        script.sh("zip -r ${reportFile} ${reportDir}")
                    }
                    if (testingConfiguration.webex) {
                        QEATestingUtils.sendWebexNotifications(script,testingConfiguration,'Execution Failed',testingConfiguration.webex.attachmentRequired)
                    }
                    script.echo('Timeout occured')
                }
            }
        } else {
            script.echo('Execution Started')
            script.sh(commandExec)
        }

        if (reportDir) {
            script.sh("zip -r ${reportFile} ${reportDir}")
        }

        if (testingConfiguration.postCommands) {
	    QEATestingUtils.runCommands(script,'post',testingConfiguration) 
            
        }
    }    
}
