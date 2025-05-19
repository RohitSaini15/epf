package com.cigna.testing

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class SeleniumParallelTestSpec extends JenkinsPipelineSpecification {
    class Script {
         def env = [PATH: "stuff"],
         JOB_NAME = 'test/job/here'

         }
        def scm = [
            userRemoteConfigs: [
                [url: 'https://github.sys.cigna.com/cigna/cxtregsuite.git']
            ],
            branches: [
                [name: 'selenium-parallel-test',],
            ]
        ]
        def remoteConfigs = [
        [
            url: "https://github.sys.cigna.com/cigna/cxtregsuite.git"
        ]
    ]

    def script = new Script()
    def setup() {
        script.getBinding().setVariable( "JOB_NAME", 'job/name/here' )
        explicitlyMockPipelineVariable('steps')
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("scm")
        explicitlyMockPipelineVariable("ZEPHYR_API_TOKEN")
        getPipelineMock("scm.getProperty")('userRemoteConfigs') >> remoteConfigs
      
        echo "testing spec doc!!"
    }
   
    def """Testing Mavenrun"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration=[
            branchPattern: '.*',
            testType: 'SeleniumParallel',
            gitRepo: 'cxtregsuite',
            gitcredentialID: 'Giri-GitHub',
            zephyrCredentialId: 'Giri-Zephyr',
            zephyrReleaseId: 1478,
            parallel: true,
            dataTableScript: 'dataTable.sh',
            testCaseIds: '1:10',
            batchCount: 4,
            featureFilePath: 'src/test/resources/parallel'
        ]
        mavenBuild.config.maven=[authSettings: mavenauth]
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        mavenBuild.runMaven() 

        then:
       // def e = thrown(UnsupportedOperationException)
        //  e.message == "Please provide a valid Zephyr release id"
        1 * getPipelineMock("configFileProvider.call")(*_)
        1 * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("sh")({ it ==~ /mvn -B -q -s .* clean verify/ })

        where:
        mavenauth << [true,false]
       // zephyrid << [null,1478]
       // exceptionerror << [true,false]
    }
    def """Testing Mavenrun failure case for zephyrid and mavenauth"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration=[
            branchPattern: '.*',
            testType: 'SeleniumParallel',
            gitRepo: 'cxtregsuite',
            gitcredentialID: 'Giri-GitHub',
            zephyrCredentialId: 'Giri-Zephyr',
            zephyrReleaseId: null,
            parallel: true,
            dataTableScript: 'dataTable.sh',
            testCaseIds: '1:10',
            batchCount: 4,
            featureFilePath: 'src/test/resources/parallel'
        ]
       // mavenBuild.config.maven=[authSettings: mavenauth]
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        mavenBuild.runMaven() 

        then:
         def e = thrown(UnsupportedOperationException)
         e.message == "Please provide a valid Zephyr release id"
        
        
    }
  /*  def """Testing featurefile creation"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration=[
            testCaseIds: '1:10',    
            dataTableScript: 'dataTable.sh',
            batchCount: 5,
            featureFilePath: 'fpath'
        ]
        //mavenBuild.testingConfiguration=[testCaseIds: test]
       // mavenBuild.testingConfiguration=[batchCount: count]
        mavenBuild.featureFileCreation()

        then:
        1 * getPipelineMock("sh")([script: 'chmod +x dataTable.sh'])
        1 * getPipelineMock("sh")({/[script: 'dataTable.sh '1:10' 5 fpath returnStdout\: true']/})
      //  1 * getPipelineMock("echo")({ it ==~ /Feature file creation error. .*})  
       // 1 * getPipelineMock("stash")(*_)
       // noExceptionThrown()
         
       //where:
        //test << ["1:10"," "]
        //count << [5,3,2,1]
    }
    */
    def """Testing featurefile creation batch count limit exceeds  failures"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        
        mavenBuild.testingConfiguration=[
            testCaseIds: '1:10',    
            dataTableScript: '',
            batchCount: 5,
            featureFilePath: 'fpath'
        ]
       
        mavenBuild.featureFileCreation()
        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Please provide a data table creation shell script"
    }
    /* def """Testing featurefile creationmax batched exceeded  failures"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        
        mavenBuild.testingConfiguration=[
            testCaseIds: '1:10',    
            dataTableScript: 'test.sh',
            batchCount: 5,
            featureFilePath: 'fpath'
        ]
       
        int batchIteration=20,maxBatches=2,testCaseCount=10
        int recommendedCount = Math.ceil(testCaseCount / maxBatches)
        mavenBuild.featureFileCreation()
       
        
        then:
        if(batchIteration > maxBatches)
        {
            def e = thrown(UnsupportedOperationException)
            e.message == "Batch count is too low"
        }
      
        
   } */
   
    
    def """Testing checkoutproject method"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration = [:]
        mavenBuild.testingConfiguration=[
                  branchPattern: '.*',
            testType: 'SeleniumParallel',
            gitRepo: 'cxtregsuite',
            gitcredentialID: 'Giri-GitHub'
        ]
        mavenBuild.checkoutProject()
        then:
        1 * getPipelineMock("checkout")(*_)
    }
    def """Testing checkoutproject method for failure cases"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration = [:]
        mavenBuild.testingConfiguration=[
            gitRepo: null,
            gitcredentialID: null
        ]
        mavenBuild.checkoutProject()
        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Please provide a valid Git Repo"
              
    }
    def """Testing checkoutproject method for failure cases credential id"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration = [:]
        mavenBuild.testingConfiguration=[
               branchPattern: '.*',
            testType: 'SeleniumParallel',
            gitRepo: 'cxtregsuite',
            gitcredentialID: null
        ]
        mavenBuild.checkoutProject()
        then:
        def e1 = thrown(UnsupportedOperationException)
        e1.message == "Please provide a valid Git Credential Id"
       
    }

    def """Testing featurefile creation for testcase id failures"""() {

        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration=[
            testCaseIds: null,    
            dataTableScript: 'dataTable.sh',
            batchCount: 5,
            featureFilePath: 'fpath'
        ]
        mavenBuild.featureFileCreation()

        then:
        def e1 = thrown(UnsupportedOperationException)
        e1.message == "Please provide a valid Test Case id ranges"
    }
   /* def """ the containers are added and the proper steps run"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration = [:]
        mavenBuild.testingConfiguration=[
            branchPattern: '.*',
            testType: 'SeleniumParallel',
            gitRepo: 'cxtregsuite',
            gitcredentialID: 'Giri-GitHub',
            zephyrCredentialId: 'Giri-Zephyr',
            zephyrReleaseId: 1478,
            parallel: true,
            dataTableScript: 'dataTable.sh',
            testCaseIds: '1:10',
            batchCount: 4,
            featureFilePath: 'src/test/resources/parallel'
        ]
        List credsList = []
        List configsList = []
        mavenBuild.runImpl()

        then:
        
        1 * getPipelineMock("withCredentials")({ it ==~ /.*/ //})
       // 1 * getPipelineMock("podTemplate")({ it ==~ /.*/ })
       // 1 * getPipelineMock("node")({ it ==~ /.*/ })
       // 1 * getPipelineMock("configFileProvider")({ it ==~ /.*/ })
       //  1 * getPipelineMock("dir")({ it ==~ /.*/ })
      //  1 * getPipelineMock("parallel")({ it ==~ /.*/ })
    //}
    
    def """Testing pre batch execution"""() {
        when:
        def script = new Script()
        def mavenBuild = new SeleniumParallelTest(script: script)
        mavenBuild.testingConfiguration = [:]
        mavenBuild.testingConfiguration=[
            testCaseIds: '1:10',    
            dataTableScript: 'dataTable.sh',
            batchCount: 5,
            featureFilePath: '/src/test/resources/parallel'
        ]
        mavenBuild.prepBatchExecution("batch")
        then:
        1 * getPipelineMock("unstash")(*_)
        1 * getPipelineMock("sh")([script: 'rm -f -- /src/test/resources/parallel//scenario.feature'])
        1 * getPipelineMock("sh")([script: 'mv batch.feature /src/test/resources/parallel//batch.feature'])
    }
}
