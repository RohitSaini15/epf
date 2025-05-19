package com.cigna.builds

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class PythonBuildSpec extends JenkinsPipelineSpecification {
    class Script {
        def env = [PATH: "stuff"]
    }

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("artifactoryDeployerIdName")
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
    }

    def """When validatePhase is called and required configuration items are missing issues are noted"""() {
        when:
            def pythonBuild = new PythonBuild(
                config: [
                    checkmarx: [
                        settings: [
                            CX_PROJECT_TEAM_NAME: 'test'
                        ],
                        credentialsId: 'bar'
                    ],
                    sonarQube: [
                        credentialsId: 'baz',
                        mainBranch: 'test'
                    ],
                    artifactory: [
                        credentialsId: artifactoryCredentialsId
                    ]
                ]
            )

            def issues = pythonBuild.validate()

        then:
            assert issues.size() == numberOfIssues

        where:
            artifactoryCredentialsId << [null, "test"]
            numberOfIssues << [1, 0]
    }

    def """When validatePhase is called and optional configuration items are misconfigured, issues are noted"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(
                config: [
                    checkmarx: [
                        settings: [
                            CX_PROJECT_TEAM_NAME: 'test'
                        ],
                        credentialsId: 'bar'
                    ],
                    sonarQube: [
                        credentialsId: 'baz',
                        mainBranch: 'test'
                    ],
                    artifactory: [
                        credentialsId: 'test'
                    ],
                    packagingTool: packagingTool
                ]
            )
            def issues = pythonBuild.validate()

        then:
            assert issues.size() == numberOfIssues

        where:
            packagingTool << [null, 'pickle', 'poetry', 'pipenv']
            numberOfIssues << [0, 1, 0, 0]
    }

    def """When executeBuildAndTestStage is called tox is installed, ran,
    then a binary distribution is created and zipped for checkmarx"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(config: [:], script: script)
            pythonBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("sh")('pip install tox')
            1 * getPipelineMock("sh")('export PATH=$HOME/.local/bin:$PATH && tox')
    }

    def """When executeBuildAndTestStage is called with tox args in the config tox is installed, ran,
    then a binary distribution is created and zipped for checkmarx"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(config: [toxArgs: '-p all'], script: script)
            pythonBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("sh")('pip install tox')
            1 * getPipelineMock("sh")('export PATH=$HOME/.local/bin:$PATH && tox -p all')
    }


    def """When executeBuildAndTestStage is called and pipPath is provided by config
        then pip command is run with given value"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(
                config: [
                    pipPath: "/usr/bin/pip3"
                ],
                script: script
            )
            pythonBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("sh")("/usr/bin/pip3 install tox")
            1 * getPipelineMock("sh").call('printf "\n' +
                    '[global]\n' +
                    'index-url = https://cigna.jfrog.io/artifactory/api/pypi/pypi-repos/simple" > ~/.pip/pip.conf')
            4 * getPipelineMock("sh")(*_)
    }

    def """When executeBuildAndTestStage is called and pipPath & pypiRepo is provided by config
        then pip command is run with given value"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(
                config: [
                    pipPath: "/usr/bin/pip3",
                    pypiRepo : 'healthservice-pypi',
                ],
                script: script
            )
            pythonBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("sh")("/usr/bin/pip3 install tox")
            1 * getPipelineMock("sh").call('printf "\n' +
                    '[global]\n' +
                    'index-url = https://cigna.jfrog.io/artifactory/api/pypi/pypi-virtual/simple" > ~/.pip/pip.conf')
            4 * getPipelineMock("sh")(*_)
    }

    def """When executePublishStage is called then a series of sh steps
        are called to initialize the pypirc and run the python upload"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(
                config: [
                    artifactory: [
                        credentialsId: 'test'
                    ]
                ],
                script: script
            )
            pythonBuild.executePublishStage()

        then:
            1 * getPipelineMock("dir").call('./', *_)
            1 * getPipelineMock("sh")('pip install --upgrade pip')
            1 * getPipelineMock("withCredentials")(*_)
            1 * getPipelineMock("usernamePassword.call").call([
                'credentialsId':'test',
                'passwordVariable':'artifactoryDeployerIdToken',
                'usernameVariable':'artifactoryDeployerIdName'
            ])
            1 * getPipelineMock("sh").call('printf "\n' +
                    '[distutils]\n' +
                    'index-servers = cignapypi\n' +
                    ' \n' +
                    '[cignapypi]\n' +
                    'repository: https://cigna.jfrog.io/artifactory/api/pypi/pypi-repos" > ~/.pypirc')
            2 * getPipelineMock("sh")(*_)
            1 * getPipelineMock("sh")('pip install pipenv')
            1 * getPipelineMock("sh")(
                'pipenv install build twine && export PATH=$HOME/.local/bin:$PATH && '\
                +'pipenv run python -m build --wheel && pipenv run python -m twine upload -r cignapypi --verbose dist/*'
            )
    }

    def """When executePublishStage is called then a series of sh steps
        are called to initialize the poetry config and run the python upload using poetry packaging"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(
                config: [
                    artifactory: [
                        credentialsId: 'test'
                    ],
                    packagingTool: 'poetry'
                ],
                script: script
            )
            pythonBuild.executePublishStage()

        then:
            1 * getPipelineMock("dir").call('./', *_)
            1 * getPipelineMock("sh")('pip install --upgrade pip')
            1 * getPipelineMock("sh")('pip install poetry')
            1 * getPipelineMock("withCredentials")(*_)
            1 * getPipelineMock("usernamePassword.call").call([
                'credentialsId':'test',
                'passwordVariable':'artifactoryDeployerIdToken',
                'usernameVariable':'artifactoryDeployerIdName'
            ])
            1 * getPipelineMock("sh").call('poetry config repositories.cignapypi https://cigna.jfrog.io/artifactory/api/pypi/pypi-repos')
            1 * getPipelineMock("sh")(*_)
            1 * getPipelineMock("sh")('poetry -v publish --build -r cignapypi')
    }

    def """When executePublishStage is called then a series of sh steps
        are called to initialize the poetry config and run the python upload using poetry packaging and package directory"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(
                config: [
                    artifactory: [
                        credentialsId: 'test'
                    ],
                    packagingTool: 'poetry',
                    packageDir: 'packages/test-package'
                ],
                script: script
            )
            pythonBuild.executePublishStage()

        then:
            1 * getPipelineMock("dir").call('packages/test-package', *_)
            1 * getPipelineMock("sh")('pip install --upgrade pip')
            1 * getPipelineMock("sh")('pip install poetry')
            1 * getPipelineMock("withCredentials")(*_)
            1 * getPipelineMock("usernamePassword.call").call([
                'credentialsId':'test',
                'passwordVariable':'artifactoryDeployerIdToken',
                'usernameVariable':'artifactoryDeployerIdName'
            ])
            1 * getPipelineMock("sh").call('poetry config repositories.cignapypi https://cigna.jfrog.io/artifactory/api/pypi/pypi-repos')
            1 * getPipelineMock("sh")(*_)
            1 * getPipelineMock("sh")('poetry -v publish --build -r cignapypi')
    }

    def """When executePublishStage is called then a series of sh steps
        are called to initialize the pypirc and run the python upload to healthservice-pypi"""() {
        when:
            def script = new Script()
            def pythonBuild = new PythonBuild(
                config: [
                    artifactory: [
                        credentialsId: 'test'
                    ],
                    pypiRepo : 'healthservice-pypi'
                ],
                script: script
            )
            pythonBuild.executePublishStage()

        then:
            1 * getPipelineMock("dir").call('./', *_)
            1 * getPipelineMock("sh")('pip install --upgrade pip')
            1 * getPipelineMock("withCredentials")(*_)
            1 * getPipelineMock("usernamePassword.call").call([
                'credentialsId':'test',
                'passwordVariable':'artifactoryDeployerIdToken',
                'usernameVariable':'artifactoryDeployerIdName'
            ])
            1 * getPipelineMock("sh").call('printf "\n' +
                        '[distutils]\n' +
                        'index-servers = healthservice-pypi\n' +
                        ' \n' +
                        '[healthservice-pypi]\n' +
                        'repository: https://cigna.jfrog.io/artifactory/api/pypi/healthservice-pypi" > ~/.pypirc')
            2 * getPipelineMock("sh")(*_)
            1 * getPipelineMock("sh")('pip install pipenv')
            1 * getPipelineMock("sh")(
                'pipenv install build twine && export PATH=$HOME/.local/bin:$PATH && '\
                +'pipenv run python -m build --wheel && pipenv run python -m twine upload -r healthservice-pypi --verbose dist/*'
        )
    }
}