package com.cigna.testing

import com.cigna.SinglePodTest
import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.GoEnvBuilder

class QEAZephyrTestSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """When validate is called and required configuration items are missing issues are noted"""() {
        when:
        def QEAZephyrTest = new QEAZephyrTest(
            script: script,
            psc: psc
        )

        QEAZephyrTest.testingConfiguration = [
                testType: testType,
                testEnvironment: testEnvironment,
                jsonInputFilePath: jsonInputFilePath,
                resultPath: resultPath,
                endDate: endDate,
                startDate: startDate,
                cycleName: cycleName,
                testRepositoryPath: testRepositoryPath,
                automationFramework: automationFramework,
                projectId: projectId,
                releaseId: releaseId,
                folderId: folderId,
                folderName: folderName,
                APIToken: APIToken,
                RLOC: RLOC,
                FLOC: FLOC
          
        ]
        simulatePodTemplate(psc, QEAZephyrTest)
        def issues = QEAZephyrTest.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        testType <<           ['test',   'test',   'test','test',  'test',  'test', 'test']
        testEnvironment <<    ['test',   'test',   'test','test',  'test',  null,   'test']
        jsonInputFilePath <<  ['test',   'test',   'test','test',  'test',  'test',  null]
        resultPath <<         ['test',   'test',   'test', null,    null,   'test', 'test']
        endDate <<            ['test',   'test',   'test', null,   'test',  null,   'test']
        startDate <<          ['test',   'test',   'test', null,   'test',  'test',  null]
        cycleName <<          ['test',   'test',   'test', null,   'test',  'test', 'test']
        testRepositoryPath << ['test',   'test',   'test', null,   'test',  'test', 'test']
        automationFramework <<['test',   'test',   'test', null,   'test',  'test', 'test']
        projectId <<          ['test',   'test',   'test', null,   'test',  'test', 'test']
        releaseId <<          ['test',   'test',   'test', null,   'test',  'test', 'test']
        folderId <<           ['test',   'test',   'test', null,   'test',  'test', 'test']
        folderName <<         ['test',   'test',   'test', null,   'test',  'test', 'test']
        APIToken <<           ['test',   'test',   'test', null,   'test',  'test', 'test']
        RLOC <<               ['test',   'test',   'test', null,   'test',  'test', 'test']
        FLOC <<               ['test',   'test',   'test', null,   'test',  'test', 'test']
        numberOfIssues <<     [0,          0,        0,      0,      0,    1,      1]	
		
    }  

}
