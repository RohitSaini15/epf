package com.cigna.testing

import com.cigna.SinglePodTest

class RegoTestSpec extends SinglePodTest {

    def setup() {
        initScriptAndPsc()
    }

    def """When run is called, opa command is executed"""() {
        when:
        RegoTest regoTest = new RegoTest(script: script, psc: psc)
        regoTest.testingConfiguration = [
            additionalArgs: args,
            bundlePath: bundlePath,
            rootPath: rootPath
        ]
        regoTest.run()

        then:
        1 * getPipelineMock('echo')('Rego tests successful.')
        1 * getPipelineMock("sh")({
            it.returnStatus == true && it.script ==~ cmdLine
        }) >> 0
        where:
        args << [
            ['-v', '-f pretty'],
            ['-v', '-f pretty', '--bench']
        ]
        cmdLine << [
            'opa test -b . -v -f pretty tests',
            'opa test -b bundles -v -f pretty --bench root'
        ]
        bundlePath << ['.', 'bundles']
        rootPath << ['tests', 'root']
    }
}
