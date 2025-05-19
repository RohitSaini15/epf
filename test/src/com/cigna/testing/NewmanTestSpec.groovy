package com.cigna.testing

import com.cigna.SinglePodTest

class NewmanTestSpec extends SinglePodTest {

    def newmanTest

    def setup() {
        initScriptAndPsc()
        script.env.WORKSPACE = 'workspaceDir'
        newmanTest = new NewmanTest(
            script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("authString")
    }

    def """When validate is called and required configuration items are missing issues are noted"""() {
        when:
            newmanTest.testingConfiguration = [
                testType: 'Newman',
                credentialsId: credential,
                collectionsPath: collections
            ]
            def issues = newmanTest.validate()

        then:
            assert issues.size() == numberOfIssues

        where:
            collections << [null, 'test']
            credential << ['test', null]
            numberOfIssues << [1, 1]
    }

    def """When collectionPathsList is called with a collections path a list of folders at the path are returned"""() {
        when:
            newmanTest.testingConfiguration = [collectionsPath: 'collectionsDir']
            def paths = newmanTest.collectionPathsList()

        then:
            1 * getPipelineMock('sh')([
                script:'ls -l workspaceDir/collectionsDir | grep ^d | awk \'{print \$9}\'',
                returnStdout:true
            ]) >> "Collection1\nCollection2"
            assert paths == ['Collection1', 'Collection2']
    }

    def """When run is called collectionsPaths are set, datafiles are added, environmentPaths are set,
         then collections are run."""() {
        when:
            newmanTest.testingConfiguration = [
                credentialsId: 'testId',
                collectionsPath: 'collectionsDir',
                dataFile: 'testing.json'
            ]
            newmanTest.deploymentConfiguration = [sdlcEnvironment: "Non-Prod"]
            newmanTest.run()

        then:
            1 * getPipelineMock("sh")([
                    script:"ls -l workspaceDir/collectionsDir | grep ^d | awk '{print \$9}'",
                    returnStdout:true]) >> "Collection1"
            1 * getPipelineMock("sh")([
                    script:'[ -e workspaceDir/collectionsDir/Collection1/Non-Prod-environment.json ]',
                    returnStatus:true]) >> 0
            1 * getPipelineMock("withCredentials")(_)
            1 * getPipelineMock("string.call")([
                    credentialsId:'testId',
                    variable:'authString'
                ])
            1 * getPipelineMock("sh")({ 
                it == 'newman run workspaceDir/collectionsDir/Collection1/run.json '\
                + '-d workspaceDir/collectionsDir/Collection1/testing.json '\
                + '-e workspaceDir/collectionsDir/Collection1/Non-Prod-environment.json '\
                + '-k --env-var \'BASIC_AUTH\'=\'Mock Generator for [authString]\''
                })
    }
}