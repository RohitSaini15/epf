package com.cigna.ruleengine

/**
 *Declaration that defines the validruleengine
 */
class ValidRuleengine extends Ruleengine {
    String containerName = 'test'
    Map<String, Object> additionalPodConfig = [
        volumes: [],
        containers: [
            [
                name: "${containerName}"
            ],
        ],
    ]

    @Override
    void runApplication() {
        script.echo('deploying...')
    }
}
