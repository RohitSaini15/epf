package com.cigna.linting

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class PlzLintingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
    }

    def """When Plz Lint with verbose options, it runs with verbosity set, otherwise it doesn't"""() {
        when:
            def lintingPhase = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        plz: wherePlzConfig,
                    ]
                ], script: script)
            lintingPhase.runPlease()

        then:
            withVerbosity * getPipelineMock("sh")({it == 'plz build //... -i lint --show_all_output -vvv'})
            withoutVerbosity * getPipelineMock("sh")({it == 'plz build //... -i lint --show_all_output'})

        where:
            wherePlzConfig << [[verbosityFlag: '-vvv'], [:]]
            withVerbosity << [1, 0]
            withoutVerbosity << [0, 1]
    }

    def """When Plz Lint with runLintAsTest set to true, it will run linting as a please test target"""() {
        when:
            def lintingPhase = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        plz: wherePlzConfig
                    ]
                ],
                script: script)
            
            lintingPhase.runPlease()

        then:
            withVerbosity * getPipelineMock("sh")({it == 'plz test //... -i lint --show_all_output -vvv'})
            withoutVerbosity * getPipelineMock("sh")({it == 'plz test //... -i lint --show_all_output'})

        where:
            wherePlzConfig << [[verbosityFlag: '-vvv', runLintAsTest: true], [runLintAsTest: true]]
            withVerbosity << [1, 0]
            withoutVerbosity << [0, 1]
    }

    def """When user provides extraArgs, those args are added to plz lint command"""() {
        when:
            def lintingPhase = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        plz: [
                            extraArgs: 'test'
                        ]
                    ]
                ], script: script
            )
            lintingPhase.runPlease()

        then:
            1 * getPipelineMock("sh")({it == 'plz build //... -i lint --show_all_output test'})
    }

    def """When user provides multiple modules only those modules are linted"""() {
        when:
            def lintingPhase = new Linting(
                config: [
                    branchPattern: 'stuff', 
                    lintingTypes: [
                        plz: [
                            runLintAsTest: true, 
                            modules: ['//module/aws/module1', '//module/aws/module2']
                        ]
                    ],
                ],
                script: script,
            )
            lintingPhase.runPlease()  
        
        then:
            1 * getPipelineMock('sh')({it == 'plz test //module/aws/module1/... -i lint --show_all_output' + 
                ' && plz test //module/aws/module2/... -i lint --show_all_output'})

        when:
            def lintingPhasePrefix = new Linting(
                config: [
                    branchPattern: 'stuff', 
                    lintingTypes: [
                        plz: [
                            runLintAsTest: true,
                            modules: ['//module/azure/module1'],
                        ]
                    ],
                ],
                script: script,
            )
            lintingPhasePrefix.runPlease()  
        
        then:
            1 * getPipelineMock('sh')({it == 'plz test //module/azure/module1/... -i lint --show_all_output'})
    }

    def """When lint is executed with modules configured but empty, 
        nothing is targeted for linting"""() {
        when:
            def linting = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        plz: [
                            runLintAsTest: true,
                            modules: []
                        ]
                    ],
                ],
                script: script
            )

            linting.runPlease()
        
        then:
            1 * getPipelineMock('echo')({it == 'Modules is specified, but the list is empty. Nothing to lint.'})
    }
}