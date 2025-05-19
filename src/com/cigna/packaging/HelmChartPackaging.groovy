package com.cigna.packaging

import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Defines Helm Chart packaging. Assumes that a build of some sort has already been completed. We'll have stash stuff at
 * the end of the build and unstash it here.
 */
@SuppressWarnings(['DuplicateMapLiteral', 'DuplicateListLiteral'])
class HelmChartPackaging extends Packaging {
    protected static final String HELM_LINT = ' helm lint . ' \
              + '$(for x in tests/values/*.yaml; do echo -n " -f $x"; done;)'
    protected static final String HELM_TEST_V2 = ' helm unittest -u . '
    protected static final String HELM_TEST_V3 = ' helm unittest -u --helm3 . '
    protected static final String HELM_VER_V2 = 'v2'
    protected static final String HELM_VER_V3 = 'v3'

    HelmChartPackaging() {
        additionalValidationItems = [
            'helm.credentialsId',
            'chart.org',
            'chart.channel',
            'dockerRegistry',
            'chart.name',
            'chart.helmVer']
    }
    String helmV2Name = 'helmv2'
    String helmV2Image = 'openshift/helm'
    String helmV2Version = 'latest'

    String helmV3Name = 'helmv3'
    String helmV3Image = 'openshift/helm'
    String helmV3Version = 'latest'

    @Override
    Boolean prePodConfig() {
    List<Map> env = [[
                     name : 'HOME',
                     value: '/root'
                 ]
        ]
        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            helmV2Name,
            "${helmV2Image}:${helmV2Version}",
            100,
            1000,
            500,
            1000,
            env
        )

        containerTemplate.addContainer(
            helmV3Name,
            "${helmV3Image}:${helmV3Version}",
            100,
            1000,
            500,
            1000,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(helmV2Name),
                            containerTemplate.getContainer(helmV3Name)]
        ]
        super.prePodConfig()
    }
    /*
     * Main entry method for deploy a phase.
     */

    @Override
    void packageApplication() {
        if (config.chart.helmVer?.equalsIgnoreCase(HELM_VER_V2)) {
            executeHelmTests(helmV2Name, HELM_TEST_V2)
            executeHelmPush(helmV2Name, 'registry login --insecure', 'registry push --insecure', 'registry')
        }
        if (config.chart.helmVer?.equalsIgnoreCase(HELM_VER_V3)) {
            executeHelmTests(helmV3Name, HELM_TEST_V3)
            executeHelmPush(helmV3Name, 'quay login -k', 'quay push -k', 'quay')
        }
    }

    void executeHelmPush(final String cntName, final String lgnCmd, final String pshCmd, final String helmCmd) {
        String helmPush = " cd ${config.chart.name} && " \
              + " helm ${pshCmd} " \
              + " --namespace ${config.chart.org} " \
              + " --channel ${config.chart.channel} " \
              + " ${config.dockerRegistry}/${config.chart.org} "
        script.container(cntName) {
            if (config.chart?.push ?: false) {
                script.withCredentials(
                    [
                        script.usernamePassword(
                            credentialsId: config.helm.credentialsId,
                            passwordVariable: 'QUAY_TOKEN',
                            usernameVariable: 'QUAY_ROBOT')
                    ]) {
                    script.sh(" helm ${lgnCmd} -u " \
                          + " ${script.QUAY_ROBOT} " \
                          + " -p ${script.QUAY_TOKEN} " \
                          + " ${config.dockerRegistry} ")
                    script.sh(helmPush)
                    script.sh(" helm ${helmCmd} logout ${config.dockerRegistry} ")
                }
            } else {
                script.echo('Skipping the Chart Push. End of Tests')
            }
        }
    }

    void executeHelmTests(final String cntName, final String runTests) {
        script.container(cntName) {
            script.sh('pwd && ls -ltr')
            script.dir("${config.chart.name}") {
                script.sh('pwd && ls -ltr')
                script.sh(HELM_LINT)
                script.sh(runTests)
            }
        }
    }
}
