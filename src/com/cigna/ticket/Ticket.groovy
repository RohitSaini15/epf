package com.cigna.ticket

import com.cigna.base.Phase
import com.cigna.common.utils.Utils

/**
 * Abstract base class that defines the contract for ticket job strategies
 */
abstract class Ticket extends Phase {
    Ticket() {
        baseValidationItems = ['ticketType']
        groupID = 'deploy'
    }
    protected Map<String, Object> ticketConfiguration
    protected String implementationComments
    String ticketType

    /**
     * Setter for ticketConfiguration, needed for validation reasons
     * @param ticketConfiguration The config map to apply to both ticketConfiguration and config
     */
    void setTicketConfiguration(Map<String, Object> ticketConfiguration) {
        this.config = ticketConfiguration
        this.ticketConfiguration = ticketConfiguration
    }

    void fullRun(
        List credsList = [],
        List configsList = []
    ) {

        String cloudNameOrPodGroup = Utils.cloud(config)
        script.echo("Running Ticket phase in cloud/group '${cloudNameOrPodGroup}'.")

        psc.podSelector.select(psc, podTemplateContainerName, cloudNameOrPodGroup) {
            script.withCredentials(credsList) {
                script.configFileProvider(configsList) {
                    moveFiles('begin')
                    script.dir(baseDirectory) {
                        this.run(cloudNameOrPodGroup)
                    }
                    moveFiles('end')
                }
            }
        }
    }

    /**
     * Encompasses the steps to take when running the given ticket type
     */
    abstract void run(String cloudName)

    abstract void updateState(String state)

    /**
     * Perform any necessary checks before pre-deployment tests
     * @throws Exception if deployment should be skipped
     */
    abstract void readyToDeploy()
}
