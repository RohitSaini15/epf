package com.cigna.common.request

import com.cigna.SinglePodTest

class CurlRequestorSpec extends SinglePodTest {
    CurlRequestor curlRequestor

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
        psc.podSelector.podCloudStack.push('test-cloud')
        curlRequestor = new CurlRequestor(psc, script)
    }

    def """When evaluateEnvVars is passed in CurlRequestor, expand groovy envVar
        in curl Request to UCD"""() {
        when:
        def r = curlRequestor.evaluateMapEnvVars([
                value: 'simpleValue',
                list : ['listItem1', 'listItem2'],
                map  : [mapKey1: 'mapValue1', mapKey2: '$startsWithDollar'],
        ])

        then:
        1 * getPipelineMock("sh")([script: 'echo "$startsWithDollar"', returnStdout: true]) >> 'startsWithDollar'
        assert r == [value: 'simpleValue', list: ['listItem1', 'listItem2'], map: [mapKey1: 'mapValue1', mapKey2: 'startsWithDollar']]

    }

    def "When Groovy map is specified in config, use requestJson body to send method request"() {
        given:
        def patternString = '#\\!\\/bin\\/sh -e\ncurl -sSL -o .* -w "\\%\\{http_code\\}" '\
                  + '-k -X PUT -m 60 '\
                  + "-u 'Mock Generator for \\[userId\\]' "\
                  + "-H 'Content-Type: application\\/json' "\
                  + "-H 'Accept: application\\/json' "\
                  + '-d \\@.*\\.json '\
                  + "'https\\:\\/\\/fakeUrl\\.com'"
        explicitlyMockPipelineVariable("userId")

        explicitlyMockPipelineStep('updateGitStatus')
        when:
        def r = curlRequestor.requestJson(
                'https://fakeUrl.com',
                'Creds',
                'PUT',
                [
                        value: 'simpleValue',
                        list : ['listItem1', 'listItem2'],
                        map  : [mapKey1: 'mapValue1', mapKey2: '$startsWithDollar'],
                ]
        )

        then:
        1 * getPipelineMock("sh")({
            it['script'] ==~ /${patternString}/
        }) >> '200'
        1 * getPipelineMock("sh")({
            it['script'] ==~ /cat .*response-.*\.json/
        }) >> '{}'
        1 * getPipelineMock("sh")([script: 'echo "$startsWithDollar"', returnStdout: true]) >> 'startsWithDollar'
    }

    def "When Groovy map is specified in config, use requestJsonWithLiteralCred body to send method request"() {
        given:
        def patternString = '#\\!\\/bin\\/sh -e\ncurl -sSL -o .* -w "\\%\\{http_code\\}" '\
                  + '-k -X PUT -m 60 '\
                  + "-u 'Creds' "\
                  + "-H 'Content-Type: application\\/json' "\
                  + "-H 'Accept: application\\/json' "\
                  + '-d \\@.*\\.json '\
                  + "'https\\:\\/\\/fakeUrl\\.com'"

        explicitlyMockPipelineVariable("token")

        explicitlyMockPipelineStep('updateGitStatus')
        when:
        curlRequestor.setContainerName("test-name")
        def r = curlRequestor.requestJsonWithLiteralCred(
                'https://fakeUrl.com',
                'Creds',
                'PUT',
                [
                        value: 'simpleValue',
                        list : ['listItem1', 'listItem2'],
                        map  : [mapKey1: 'mapValue1', mapKey2: '$startsWithDollar'],
                ]
        )

        then:
        1 * getPipelineMock("sh")({
            it['script'] ==~ /${patternString}/
        }) >> '200'
        1 * getPipelineMock("sh")({ it['script'] ==~ /cat .*response-.*\.json/ }) >> '{}'
        1 * getPipelineMock("sh")([script: 'echo "$startsWithDollar"', returnStdout: true]) >> 'startsWithDollar'
    }


    def "When Groovy map is specified in config, use requestJsonWithToken body to send method request"() {
        given:
        def patternString = '#\\!\\/bin\\/sh -e\ncurl -sSL -o .* -w "\\%\\{http_code\\}" '\
                  + '-k -X PUT -m 60 '\
                  + "--header 'Authorization\\: Bearer Mock Generator for \\[bearerToken\\]' "\
                  + "-H 'Content-Type: application\\/json' "\
                  + "-H 'Accept: application\\/json' "\
                  + '-d \\@.*\\.json '\
                  + "'https\\:\\/\\/fakeUrl\\.com'"

        explicitlyMockPipelineVariable("bearerToken")

        explicitlyMockPipelineStep('updateGitStatus')
        when:
        curlRequestor.containerName = "test-name"
        def r = curlRequestor.requestJsonWithToken(
                'https://fakeUrl.com',
                'token',
                'PUT',
                [
                        value: 'simpleValue',
                        list : ['listItem1', 'listItem2'],
                        map  : [mapKey1: 'mapValue1', mapKey2: '$startsWithDollar'],
                ]
        )

        then:
        1 * getPipelineMock("sh")({
            it['script'] ==~ /${patternString}/
        }) >> '200'
        1 * getPipelineMock("sh")({ it['script'] ==~ /cat .*response-.*\.json/ }) >> '{}'
        1 * getPipelineMock("sh")([script: 'echo "$startsWithDollar"', returnStdout: true]) >> 'startsWithDollar'
    }

    def "bodyToFormParams should transform map into form data string and handle various cases"() {
        given:
        Map requestBodyMap = inputMap

        when:
        String result = curlRequestor.bodyToFormParams(requestBodyMap)

        then:
        result == expectedOutput

        where:
        inputMap                                      | expectedOutput
        [name: 'John', age: '30']                     | '-F "name=John" -F "age=30"'
        [name: 'John "Doe"', age: '30']               | '-F "name=John \\"Doe\\"" -F "age=30"'
    }
}