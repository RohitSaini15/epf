import com.cigna.base.JenkinsIO
import com.cigna.base.Phase as PipelinePhase
import com.cigna.common.exception.EndPipelineException
import com.cigna.common.logging.ConsoleLogger
import com.cigna.common.notification.Notification
import com.cigna.common.phases.PhaseExecution
import com.cigna.common.phases.PhaseListUtils
import com.cigna.common.phases.PhaseLoader
import com.cigna.common.pipelinemetrics.DynatraceMetrics
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.state.PipelineStateContext
import hudson.Functions

/**
 * This takes the given closure and converts it to a map which gets used throughout the code base.
 * This is a common Jenkins DSL making strategy, see https://jenkins.io/blog/2016/04/21/dsl-plugins/
 * for an example use case
 *
 * @param body The closure body given to cignaBuildFlow global
 *
 * @return The config map that was derived from the given Closure body
 */
Map<String, Object> mapFromClosure(Closure body) {
    Map<String, Object> config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    config.phases = config.phases.collect { it.clone() }

    config
}

/**
 * Method used to produce monitoring platform dashboard based on the FeatureFlags class
 */
void printMonitoringPlatformUrl(String cloudName) {
    switch (FeatureFlags.monitoringPlatform) {
        case 'grafana':
            ConsoleLogger.printMetricsUrl(this, cloudName)
            break
        case 'dynatrace':
            DynatraceMetrics dm = new DynatraceMetrics(cloudName, this)
            dm.printDynatraceMetricsUrl()
            break
    }
}

/**
 * Method uses the console logger to log the error, sets the build result to 'FAILURE', uses the
 * provided notification to notifyWithAllMethods a build failure then finally uses the error step
 * to display the error message and send an event to Splunk
 *
 * @param notification The notification object to use to notify of failures
 * @param config The config map taken from the closure body in the call method
 * @param errorMessage The error message to display in the notification and error step
 * @param errorRaised If an exception was raised, include it here and it will be included in the
 * monitoring platform output.
 */
void handleError(Notification notification, Map<String, Object> config, String errorMessage,
                 Map<String, Object> splunkEvent, Exception errorRaised = null) {
    ConsoleLogger.logJobInfo(this, 'End of Build', 'Build Failed', config, errorRaised)
    if (!FeatureFlags.debug) {
        echo("To view more detailed logs, set FeatureFlags.debug to true")
    }
    currentBuild.result = 'FAILURE'
    notification.notifyWithAllMethods('Pipeline Failed: ' + errorMessage, 'Pipeline event', 'FAILED')
    splunkEvent['job_result'] = currentBuild.result
    splunkEvent['outcome_message'] = errorMessage ?: "${errorRaised?.class?.simpleName}: Error with no message"
    printMonitoringPlatformUrl(config.cloudName)
    splunkins.send(splunkEvent)
    error(errorMessage)
}

/**
 * Turn the given body Closure into a map, set the Gitlab commit status name to EPF or whatever is
 * input, set the log history age and count in jenkins, update Gitlab with the status, notify
 * by all methods available that a Pipeline has started, validate all the phase configurations,
 * then determine which phases match the branch pattern set, and run them in order which they were
 * configured.
 *
 * @param body The closure body that cignaBuildFlow is called with
 * @param providedNotification The Notification object to use for notifications, this is useful for
 * testing purposes (Dependency Injection). If not provided a Notification object is instantiated
 */
void call(Closure body, Notification providedNotification = null) {
    Map<String, Object> config = mapFromClosure(body)
    try {
        Boolean isProductionDeployment = false
        Boolean initialProdDeploy = false
        sendSplunkConsoleLog {
            String banner = libraryResource('com/cigna/banners/bannerStart.txt')
            echo(banner)
            PipelineStateContext psc
            // necessary for injecting PSC from tests
            if (!binding.hasVariable('psc')) {
                psc = Utils.PipelineStateContext(this, config)
            } else {
                psc = binding.getVariable('psc') as PipelineStateContext
            }
            echo(FeatureFlags.asString())
            Notification notification = providedNotification ?: new Notification(
                config: config, script: this
            )

            PipelinePhase.commitStatusName = config?.commitStatusName ?: 'EPF'
            PipelinePhase.baseDirectory = config?.baseDirectory ?: './'

            printMonitoringPlatformUrl(config.cloudName)
            CommonGit commonGit = new CommonGit(config, this)
            commonGit.updateGitStatus(
                name: PipelinePhase.commitStatusName, state: 'pending', contextName: config.contextName
            )
            try {
                timestamps {
                    JenkinsIO.configureProperties(this, config)
                    notification.notifyWithAllMethods('Pipeline Started', 'Pipeline event', 'STARTED')
                    ConsoleLogger.logJobInfo(this, 'Pipeline Started', 'Pipeline Started', config)

                    if (!config?.phases) {
                        throw new UnsupportedOperationException(
                            'You must define phases to run. Please verify your configuration '
                                + 'file is correctly formatted'
                        )
                    }
                    commonGit.grabChangedFiles()
                    List<Map<String, Object>> phasesToRun = PhaseLoader.getCommonPreflightPhases(this, config)
                    phasesToRun?.addAll(PhaseLoader.phasesThatMatchPattern(this, config, commonGit.changedFiles, scm.branches[0].name))
                    if (phasesToRun) {
                        phasesToRun = PhaseLoader.loadAndValidatePhases(this, phasesToRun, psc)
                        psc.complianceValidator.loadPhases(phasesToRun)
                        initialProdDeploy = psc.complianceValidator.isProductionDeployment(phasesToRun)
                        phasesToRun = psc.complianceValidator.retrieveCompliantPhases(psc, phasesToRun, initialProdDeploy)
                        psc.splunkEvent['phases'] = PhaseListUtils.sanitizeForJson(phasesToRun)

                        isProductionDeployment = psc.complianceValidator.isProductionDeployment(phasesToRun)
                        if (isProductionDeployment && !psc.complianceValidator.isPipelineCompliant) {
                            // Log this early so that it is apparent the phases were removed
                            echo(psc.complianceValidator.PROD_WARN_MSG)
                        }

                        PhaseExecution.runPhases(this, psc, phasesToRun, config)
                    } else {
                        echo(
                            'No phase branchPatterns matched the current branch, so no actions will be '
                                + 'taken.'
                        )
                    }
                }
            } catch (EndPipelineException e) {
                // translate status for git
                String gitStatus = (e.outcome == 'FAILURE') ? 'failure' : 'success'
                // translate status for notification
                String notificationStatus = (e.outcome == 'FAILURE') ? 'FAILED' : 'COMPLETED'
                commonGit.updateGitStatus(
                    name: PipelinePhase.commitStatusName, state: gitStatus,
                    allMessage: e.localizedMessage, contextName: config.contextName
                )
                notification.notifyWithAllMethods('Pipeline Ended - ' + e.message, 'Pipeline event', notificationStatus)
                psc.splunkEvent['job_result'] = e.outcome
                psc.splunkEvent['outcome_message'] = e.message ?: "${e.class?.simpleName}: Error with no message"
                splunkins.send(psc.splunkEvent)
                throw e
            } catch (all) {
                if (FeatureFlags.showStackTraces) {
                    echo(Functions.printThrowable(all))
                }
                commonGit.updateGitStatus(
                    name: PipelinePhase.commitStatusName, state: 'failure', allMessage: "${all.localizedMessage}",
                    contextName: config.contextName
                )
                handleError(notification, config, all.localizedMessage, psc.splunkEvent, all)
            }
            if (initialProdDeploy && !psc.complianceValidator.isPipelineCompliant) {

                echo(psc.complianceValidator.PROD_WARN_MSG)
                psc.complianceValidator.addNewsArticle(psc.complianceValidator.PROD_WARN_MSG)
                psc.complianceValidator.addNewsArticle(psc.complianceValidator.SPECIFIC_FAIL_MSG)
                if (config?.correlationStrategy == 'git_commit' || config?.correlationStrategy == 'user_defined') {
                    echo('Evicting correlation id from cache. Non production deployment will need to be re-run.')
                }
                psc.complianceValidator.evict()
                psc.splunkEvent['outcome_message'] = 'Pipeline marked as failure due to Compliance Validator removing prod deployments'
                currentBuild.result = 'FAILURE'
            } else {
                psc.splunkEvent['outcome_message'] = 'All phases completed'
                currentBuild.result = 'SUCCESS'
            }
            boolean shouldAudit = true
            echo("currentBuild.result = ${currentBuild.result}, isProd ${isProductionDeployment}")
            if (currentBuild.result == 'SUCCESS' && isProductionDeployment && shouldAudit) {
                psc.complianceValidator.audit()
            }
            commonGit.updateGitStatus(
                name: PipelinePhase.commitStatusName,
                state: 'success',
                contextName: config.contextName
            )
            notification.notifyWithAllMethods('Pipeline Completed', 'Pipeline event', 'COMPLETED')
            psc.splunkEvent['job_result'] = currentBuild.result
            splunkins.send(psc.splunkEvent)

            echo(psc.complianceValidator.outputFormatted())
        }
    } catch (EndPipelineException e) {
        currentBuild.result = e.outcome
    }
}
