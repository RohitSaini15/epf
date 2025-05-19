package com.cigna.builds

import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * For builds utilzing Python.
 */
class PythonBuild extends Build {
    PythonBuild() {
        additionalValidationItems = ['artifactory.credentialsId']
        containerName = 'pythonvpython-39-ubi9-v2'
        containerImage = 'enterprise-devops/python'
        containerVersion = 'python-39-ubi9-v2'
        containerMemory = '1000Mi'
        containerCpu = '1000m'
        stashIncludePattern = ''
    }

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false) {
        List issues = []

        if (config?.packagingTool && !( ['pipenv', 'poetry'].contains(config.packagingTool) )) {
            issues.add('packagingTool must be one of "pipenv" or "poetry"')
        }

        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = [[
                             name : 'HOME',
                             value: '/tmp'
                         ]]

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
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

        additionalPodConfig = [
            volumes   : [],
            containers: [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }
    static final String ARTIFACTORY_PYPI_URL = 'https://cigna.jfrog.io/artifactory/api/pypi/pypi-repos'
    static final String ARTIFACTORY_HS_PYPI_URL = 'https://cigna.jfrog.io/artifactory/api/pypi/healthservice-pypi'
    static final String ARTIFACTORY_PYPI_INSTALL_URL = 'https://cigna.jfrog.io/artifactory/api/pypi/pypi-repos/simple'
    static final String ARTIFACTORY_HS_PYPI_INSTALL_URL = 'https://cigna.jfrog.io/artifactory/api/pypi/pypi-virtual/simple'

    /**
     * This stage will run Pip install Tox and run the Tox.ini file within the python directory.
     * PATH exporting is for non-RHEL images to work see https://github.com/pypa/pip/issues/3813
     */
    @Override
    void executeBuildAndTestStage() {
        String pypiRepo = config.pypiRepo ?: 'cignapypi'
        String packageDir = config.packageDir ?: './'
        script.sh('mkdir -p $HOME/.pip')
        List<String> pipConfContents = [
            '[global]',
            "index-url = ${pypiRepo.equals('cignapypi') ? ARTIFACTORY_PYPI_INSTALL_URL : ARTIFACTORY_HS_PYPI_INSTALL_URL}",
        ]
        String pipConfContentsText = '\n' + pipConfContents.join('\n')
        script.sh(
            "printf \"${pipConfContentsText}\" > ~/.pip/pip.conf"
        )
        String pipPath = config.pipPath ?: 'pip'
        String toxCommand = config?.toxArgs ? "tox ${config.toxArgs}" : "tox"
        script.sh("${pipPath} install --upgrade pip")
        script.sh("${pipPath} install tox")
        script.dir(packageDir) {
            script.sh('export PATH=$HOME/.local/bin:$PATH && ' + toxCommand)
        }
        script.sh('pwd')
    }

    /**
     * Initializes a .pypirc file in the current working directory
     */
    void initPypirc(def pypiRepo) {
        Map<String, String> artifactoryConfig = config.artifactory

        script.withCredentials([script.usernamePassword(
            credentialsId: "${artifactoryConfig.credentialsId}",
            passwordVariable: 'artifactoryDeployerIdToken',
            usernameVariable: 'artifactoryDeployerIdName'
        )]) {
            List<String> pypiContents = [
                '[distutils]',
                "index-servers = ${pypiRepo}",
                ' ']

            if (pypiRepo == "healthservice-pypi") {
                pypiContents.addAll([
                '[healthservice-pypi]',
                "repository: ${ARTIFACTORY_HS_PYPI_URL}"])
            } else {
                pypiContents.addAll([
                '[cignapypi]',
                "repository: ${ARTIFACTORY_PYPI_URL}"])
            }
            String pypiContentsText = '\n' + pypiContents.join('\n')
            script.sh(
                "printf \"${pypiContentsText}\" > ~/.pypirc"
            )
            script.sh(
                "printf \"\nusername: ${script.artifactoryDeployerIdName}\" >> ~/.pypirc"
            )
            script.sh(
                "printf \"\npassword: ${script.artifactoryDeployerIdToken}\" >> ~/.pypirc"
            )
        }
    }

    /**
     * Initializes a poetry config file
     */
    void initPoetryConfig(def pypiRepo) {
        Map<String, String> artifactoryConfig = config.artifactory

        script.withCredentials([script.usernamePassword(
            credentialsId: "${artifactoryConfig.credentialsId}",
            usernameVariable: 'artifactoryDeployerIdName',
            passwordVariable: 'artifactoryDeployerIdToken'
        )]) {
            if (pypiRepo == "healthservice-pypi") {
                script.sh("poetry config repositories.${pypiRepo} ${ARTIFACTORY_HS_PYPI_URL}")
            } else {
                script.sh("poetry config repositories.${pypiRepo} ${ARTIFACTORY_PYPI_URL}")
            }
            script.sh(
                "poetry config http-basic.${pypiRepo} ${script.artifactoryDeployerIdName} ${script.artifactoryDeployerIdToken}"
            )
        }
    }

    /**
     * Steps to create a python wheel and upload it to artifactory. Uses pipenv by default instead of pip since
     * pipenv supports requirements.txt files and pipfiles which makes this more flexible.
     * PATH exporting is for non-RHEL images to work see https://github.com/pypa/pip/issues/3813
     */
    void publishWheel() {
        String pypiRepo = config.pypiRepo ?: 'cignapypi'
        String pipPath = config.pipPath ?: 'pip'
        String packagingTool = config.packagingTool ?: 'pipenv'
        String packageDir = config.packageDir ?: './'

        script.dir(packageDir) {
            script.sh("${pipPath} install --upgrade pip")
            if (packagingTool == 'pipenv') {
                initPypirc(pypiRepo)
                script.sh("${pipPath} install pipenv")
                script.sh('pipenv install build twine && '\
                + 'export PATH=$HOME/.local/bin:$PATH && pipenv run python -m build --wheel'\
                + " && pipenv run python -m twine upload -r ${pypiRepo} --verbose dist/*"
                )
            } else if (packagingTool == 'poetry') {
                script.sh("${pipPath} install poetry")
                initPoetryConfig(pypiRepo)
                script.sh("poetry -v publish --build -r ${pypiRepo}")
            }
        }
    }

    @Override
    void executePublishStage() {
        publishWheel()
    }
}
