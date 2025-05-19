package com.cigna.ruleengine

import com.cigna.base.Phase
import com.cigna.common.utils.Utils

/**
 * Declaration that defines the ruleengine
 */
abstract class Ruleengine extends Phase {
    Ruleengine() {
        groupID = 'ruleengine'
    }
    void run(
        List credsList = [],
        List configsList = []
    ) {
        String stageName = ''
        String type = config.ruleengineType.toLowerCase()
        if ("${type}" == 'postgres') {
            stageName = 'PostgreSQL'
        } else if ("${type}" == 'Oracle') {
            stageName = 'Oracle'
        }
        script.stage(generateStageName("${stageName} Rules Engine")) {
            psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
                script.withCredentials(credsList) {
                    script.configFileProvider(configsList) {
                        script.dir(baseDirectory) {
                            stepsToDo()
                        }
                    }
                }
            }
        }
    }

    abstract void runApplication()

    protected void stepsToDo() {
        script.echo('Running RuleEngine')
        moveFiles('begin')
        runApplication()
        moveFiles('end')
    }
}
