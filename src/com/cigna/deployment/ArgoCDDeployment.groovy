package com.cigna.deployment

import com.cigna.base.Phase
import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Class to provide an opinionated helm deployment
 */
class ArgoCDDeployment extends Deployment {
    Boolean useRollbackCode = true

    ArgoCDDeployment() {
        additionalValidationItems = [
            'argoCd.argoCdServer',
            'argoCd.namespace',
            'argoCd.appName',
            'argoCd.credentialsId',
            'argoCd.chartPath',
        ]
        containerName = 'argocd-cliv1714-v2'
        containerImage = 'enterprise-devops/argocd-cli'
        containerVersion = '1.7.14-v2'
    }
    String vaultName = 'ansiblevlatest'
    String vaultImage = 'cigna-digital/ansible'
    String vaultVersion = 'latest'

    String deployStatus = 'Deploy'

    @Override
    Boolean prePodConfig() {

        List<Map> env = [
            [
                name : 'HOME',
                value: '/tmp'
            ]
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            1000,
            500,
            1000,
            env
        )

        env = [
            [
                name : 'HOME',
                value: '/home/jenkins/agent'
            ]
        ]

        containerTemplate.addContainer(
            vaultName,
            "${vaultImage}:${vaultVersion}",
            100,
            1000,
            500,
            1000,
            env
        )

        env = []


        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName),
                            containerTemplate.getContainer(vaultName)]
        ]
        super.prePodConfig()
    }

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        List issues = []
        boolean requireBranchPattern = true

        if (config?.argoCd?.vaultedValuesFiles && !config?.argoCd?.vaultCredentialsId) {
            additionalValidationItems += [[
                                              testString   : 'vaultCredentialsId',
                                              customMessage: 'In order to utilize the vaultedValuesFiles functionality, ' \
                      + 'you must define a vaultCredentialsId.'
                                          ]]
        }
        issues.addAll(super.validate(requireBranchPattern, phase))
        issues
    }

    @Override
    void deploy() {
        extractVaultFiles()
        executeArgoCDDeploy("${script.env.GIT_COMMIT}")
    }

    /**
     * Set argo to previous successful commit
     */
    void rollback() {
        executeArgoCDDeploy("${script.env.GIT_PREVIOUS_COMMIT}")
        if (this.ticket) {
            updateTicket('deployRollback', 'Deployment failed, attempting rollback')
        }
    }

    void extractVaultFiles() {
        if (config.argoCd.containsKey('vaultedValuesFiles')) {
            psc.podSelector.select(psc,vaultName, Utils.cloud(config)) {
                script.withCredentials(
                    [script.string(
                        credentialsId: config.argoCd.vaultCredentialsId,
                        variable: 'VAULT_PASS')
                    ]) {
                    // print vault pass to file because vault is just like that
                    script.sh("set +x; echo \'${script.VAULT_PASS}\' > vaultPass")
                    config.argoCd.vaultedValuesFiles.each { file ->
                        script.sh('ansible-vault --version')
                        script.sh(
                            "ansible-vault decrypt ${file} " +
                                '--vault-password-file vaultPass'
                        )
                        script.sh("chmod +r ${file}")
                    }
                    script.sh('rm vaultPass')
                }
            }
        }
    }

    void executeArgoCDDeploy(String targetCommitSha) {
        String argoOpts = config.argoCd.argoCdOpts ?: '--insecure'
        Integer revHistoryLimit = config.argoCd.revisionHistoryLimit ?: 3
        String pruneFlag = config.argoCd.prune ? '--prune' : ''
        Integer timeout = config.argoCd.timeout ?: 300
        Boolean autoscaling = config.argoCd.horizontalPodAutoscaling ?: false
        Boolean parameter = config.argoCd.parameter ?: false
        String parametervalue = config.argoCd.parametervalue ?: ' '
        String[] parameterValueArray = parametervalue.split(',')
        Boolean uninstallflag = config.argoCd.uninstallflag ?: false
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            script.withCredentials([
                script.string(
                    credentialsId: "${config.argoCd.credentialsId}",
                    variable: 'ARGOCD_AUTH_TOKEN'
                )
            ]) {

                withPhaseConfigEnv(["ARGOCD_SERVER=${config.argoCd.argoCdServer}", "ARGOCD_OPTS=${argoOpts}"]) {
                    if (parameter) {
                        script.echo('Parameter update in Argocd')
                        String param = ''
                        for (int i = 0; i < parameterValueArray.length; i++) {
                            String buff = parameterValueArray[i]
                            param = "$param" + " -p $buff"
                        }
                        script.sh(
                            'argocd app set'
                                + " ${config.argoCd.appName}"
                                + " ${param}"
                        )
                        script.sh(
                            'argocd app sync'
                                + " ${config.argoCd.appName}"
                                + ' --async'
                        )
                        script.sh(
                            'argocd app wait'
                                + " ${config.argoCd.appName}"
                                + ' --sync'
                                + " --timeout ${timeout}"
                        )
                    } else if (uninstallflag) {
                        script.sh(
                            'argocd app delete'
                                + " ${config.argoCd.appName}"
                        )
                    } else {
                        script.sh(
                            'argocd app create'
                                + " ${config.argoCd.appName}"
                                + " --revision ${targetCommitSha}"
                                + " --dest-namespace ${config.argoCd.namespace}"
                                + ' --dest-server https://kubernetes.default.svc'
                                + " --project ${config.argoCd.namespace}"
                                + " --repo ${config?.argoCd?.repourl ?: gitUrl()}"
                                + " --path ${config.argoCd.chartPath}"
                                + " --revision-history-limit ${revHistoryLimit}"
                                + "${argoValuesParams()}"
                                + "${argoSetProps()}"
                                + "${argoVaultedValuesFileParams()}"
                                + ' --upsert'
                                + ' --loglevel debug'
                        )
                        script.sh("argocd app sync ${config.argoCd.appName} --async ${pruneFlag}")
                        if (autoscaling) {
                            script.sh("argocd app wait ${config.argoCd.appName} --sync --timeout ${timeout}")
                        } else {
                            script.sh(
                                "argocd app wait ${config.argoCd.appName} --sync --health"
                                    + " --timeout ${timeout}"
                            )
                        }
                    }
                }
            }
        }
    }

    String argoValuesParams() {
        String valuesString = ' '
        if (config.argoCd.containsKey('valuesFiles')) {
            config.argoCd.valuesFiles.each { file ->
                valuesString = valuesString + "--values ${file} "
            }
        }
        valuesString
    }

    String argoSetProps() {
        String setProps = ' '
        if (config.argoCd.containsKey('setValues')) {
            config.argoCd.setValues.each { value ->
                script.echo(value)
                setProps = setProps + "--helm-set \"${value}\" "
            }
        }
        setProps
    }

    String argoVaultedValuesFileParams() {
        String valuesFileParams = ' '
        if (!config.argoCd.vaultedValuesFiles) {
            return valuesFileParams
        }

        config.argoCd.vaultedValuesFiles.each { file ->
            valuesFileParams = valuesFileParams + "--values-literal-file ${file} "
        }
        valuesFileParams
    }

}
