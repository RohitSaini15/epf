package com.cigna.packaging

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class HelmChartPackagingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
            GIT_BRANCH: 'stuff',
            GIT_COMMIT: 'stuff',
            GIT_PREVIOUS_COMMIT: 'stuff',
            GIT_PREVIOUS_SUCCESSFUL_COMMIT: 'stuff'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
    }

    def '''When users upload a V2 Chart'''() {
        when:
            Packaging helmChartPackaging = new HelmChartPackaging(
                config: [
                    packagingType : 'helmChart',
                    branchPattern  : 'fake',
                    sdlcEnvironment: 'fake',
                    dockerRegistry : 'fake.com',
                    chart          :[
                        org: 'fake-org',
                        channel: 'fake',
                        name: 'fake-chart',
                        helmVer: 'v2',
                        push: true
                    ],
                    helm            :[
                        credentialsId: 'fake',
                    ],

                ],
                script: script
            )
            explicitlyMockPipelineVariable('QUAY_ROBOT')
            explicitlyMockPipelineVariable('QUAY_TOKEN')
            helmChartPackaging.packageApplication()

        then:
        2 * getPipelineMock("sh").call('pwd && ls -ltr')
        1 * getPipelineMock("sh").call(' helm lint . $(for x in tests/values/*.yaml; do echo -n " -f $x"; done;)')
        1 * getPipelineMock("sh").call(' helm unittest -u . ')
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock("sh").call(' helm registry login --insecure -u  Mock Generator for [QUAY_ROBOT]  -p Mock Generator for [QUAY_TOKEN]  fake.com ')
        1 * getPipelineMock("usernamePassword.call").call(['credentialsId':'fake', 'passwordVariable':'QUAY_TOKEN', 'usernameVariable':'QUAY_ROBOT'])
        1 * getPipelineMock("sh").call(' cd fake-chart &&  helm registry push --insecure  --namespace fake-org  --channel fake  fake.com/fake-org ')
        1 * getPipelineMock("sh").call(' helm registry logout fake.com ')
    }
    def '''When users upload a V3 Chart'''() {
        when:
            Packaging helmChartPackaging = new HelmChartPackaging(
                config: [
                    packagingType : 'helmChart',
                    branchPattern  : 'fake',
                    sdlcEnvironment: 'fake',
                    dockerRegistry : 'fake.com',
                    chart          :[
                        org: 'fake-org',
                        channel: 'fake',
                        name: 'fake-chart',
                        helmVer: 'v3',
                        push: true
                    ],
                    helm            :[
                        credentialsId: 'fake',
                    ],

                ],
                script: script
            )
            explicitlyMockPipelineVariable('QUAY_ROBOT')
            explicitlyMockPipelineVariable('QUAY_TOKEN')
            helmChartPackaging.packageApplication()

        then:
        2 * getPipelineMock("sh").call('pwd && ls -ltr')
        1 * getPipelineMock("sh").call(' helm lint . $(for x in tests/values/*.yaml; do echo -n " -f $x"; done;)')
        1 * getPipelineMock("sh").call(' helm unittest -u --helm3 . ')
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock("sh").call(' helm quay login -k -u  Mock Generator for [QUAY_ROBOT]  -p Mock Generator for [QUAY_TOKEN]  fake.com ')
        1 * getPipelineMock("usernamePassword.call").call(['credentialsId':'fake', 'passwordVariable':'QUAY_TOKEN', 'usernameVariable':'QUAY_ROBOT'])
        1 * getPipelineMock("sh").call(' cd fake-chart &&  helm quay push -k  --namespace fake-org  --channel fake  fake.com/fake-org ')
        1 * getPipelineMock("sh").call(' helm quay logout fake.com ')
    }
    def '''When users want to only test a chart'''() {
        when:
            Packaging helmChartPackaging = new HelmChartPackaging(
                config: [
                    packagingType : 'helmChart',
                    branchPattern  : 'fake',
                    sdlcEnvironment: 'fake',
                    dockerRegistry : 'fake.com',
                    chart          :[
                        org: 'fake-org',
                        channel: 'fake',
                        name: 'fake-chart',
                        helmVer: 'v2',
                        push: false
                    ],
                    helm            :[
                        credentialsId: 'fake',
                    ],

                ],
                script: script
            )
            explicitlyMockPipelineVariable('QUAY_ROBOT')
            explicitlyMockPipelineVariable('QUAY_TOKEN')
            helmChartPackaging.packageApplication()

        then:
        2 * getPipelineMock("sh").call('pwd && ls -ltr')
        1 * getPipelineMock("sh").call(' helm lint . $(for x in tests/values/*.yaml; do echo -n " -f $x"; done;)')
        1 * getPipelineMock("sh").call(' helm unittest -u . ')
    }
    def '''When users don't supply a push option'''() {
        when:
            Packaging helmChartPackaging = new HelmChartPackaging(
                config: [
                    packagingType : 'helmChart',
                    branchPattern  : 'fake',
                    sdlcEnvironment: 'fake',
                    dockerRegistry : 'fake.com',
                    chart          :[
                        org: 'fake-org',
                        channel: 'fake',
                        name: 'fake-chart',
                        helmVer: 'v2',
                    ],
                    helm            :[
                        credentialsId: 'fake',
                    ],

                ],
                script: script
            )
            explicitlyMockPipelineVariable('QUAY_ROBOT')
            explicitlyMockPipelineVariable('QUAY_TOKEN')
            helmChartPackaging.packageApplication()

        then:
        2 * getPipelineMock("sh").call('pwd && ls -ltr')
        1 * getPipelineMock("sh").call(' helm lint . $(for x in tests/values/*.yaml; do echo -n " -f $x"; done;)')
        1 * getPipelineMock("sh").call(' helm unittest -u . ')
    }
}