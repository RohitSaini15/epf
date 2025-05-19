package com.cigna.common.kubernetes

import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import spock.lang.Ignore

@Ignore
class CacheGeneratorSpec extends JenkinsPipelineSpecification {

    def cacheGenerator
    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineVariable("BUCKET_NAME")
        explicitlyMockPipelineStep("updateGitStatus")
    }

    def """When phaseCache is true, check to see if a folder exists with an sh step
        then  generateFolder is called with a given folderName to create a folder"""() {
        given:
            def folderName = "test"

        when:
            CacheGenerator cacheGenerator = new CacheGenerator(script: script)
            cacheGenerator.generateFolder(folderName)
        
        then:
            1 * getPipelineMock("sh").call('mc mb --insecure --ignore-existing minio/$BUCKET_NAME/test/')
    }

    def """When deleteFolder is called with a given folderName then a sh step is run
        to verify folder exists and then deletes the folder in another sh step if it exists"""() {
        given:
            def folderName = "test"
        
        when:
            CacheGenerator cacheGenerator = new CacheGenerator(script: script)
            cacheGenerator.deleteFolder(folderName)
        
        then:
            1 * getPipelineMock("sh").call('mc rm -q --insecure --force --recursive minio/$BUCKET_NAME/test 1>/dev/null || true')

    }
}
