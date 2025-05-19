package com.cigna.testing

import com.cigna.common.request.CurlRequestor
import com.cigna.common.kubernetes.PodTemplateCreator
import jenkins.plugins.http_request.HttpMode
import jenkins.plugins.http_request.MimeType
import groovy.json.JsonSlurper
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.base.Phase
import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.Utils

/**
 * Defines testing steps for Zephyr
 */
class QEAZephyrTest extends Testing {
    QEAZephyrTest() {
        containerName = 'qeadynamiczbotvlatest'
        testType = 'integration'
        additionalValidationItems = [
          'jsonInputFilePath',
          'testEnvironment'
        ]
        containerImage = 'qea-coreops/qea_dynamic_zbot'
        containerVersion = 'latest'
    }

    Boolean response
    String urlBase
    String urlBaseExecute
    String urlBaseDelete
    String newAPIToken
    String zbotIp
    Integer scheduledTestId
    Integer vortexTestId
    String testType = 'integration'
    String zbotAgentMachine = testingConfiguration?.zbotAgentMachine   
    String rloc
    String aloc
    protected String containerName = 'zbot'
    protected String containerImage = 'qea-coreops/qea_dynamic_zbot'
    protected String containerVersion = 'latest'
    protected Map zephyrConfiguration
    Map<String, ?> testResponse
    CurlRequestor curlRequestor
    String floc
    String zbotname
    String endDt
    String startDt
    String cyclName
    String phName
    String repoPath
    String jname
    String pid
    String rid
    String tid
    String framew
    String id
    private final Map<String, String> zephyrUrlMap = [
        'dev': 'https://dev-cigna.yourzephyr.com',
        'prod': 'https://cigna.yourzephyr.com',
    ]

    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            100,
            1000,
            1000,
            1000,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }
      

    @Override
    void runImpl() {
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {            
            String urlBase = updateZbotProps()
            String zbotname = runZbotShellScript()
            script.echo(zbotname)
            String id = nowCreateVortex(zbotname, urlBase)
            script.echo(id)
            String res = (id.split(',')[0]).split(':')[1].trim()
            script.echo(res)
            executeVortex(res, urlBase)
            deleteVortex(res, urlBase)            
//             deleteWS()
        }
    }

  
//     Void deleteWS() {
//         String wsPath = script.sh(script: 'pwd', returnStdout: true)
//         script.echo(wsPath)
//         script.dir("${wsPath}") {
//             wsPath = wsPath.trim() + '/*'
//             script.echo(wsPath)
//             script.sh("rm -rfv ${wsPath}")
//             script.sh('pwd')
//             script.sh('ls')
//         }
//     }
    String updateZbotProps() {
        script.withCredentials([
                script.string(
                    credentialsId: "${testingConfiguration.credentialsId}", variable: 'secret'
                )
            ]) {           
            String zbotSname = 'QEABTZbot'
            urlBase = zephyrUrlMap[(testingConfiguration.testEnvironment.toLowerCase())]            
            script.sh("sed -i 's|\${zephyr.serverurl}|$urlBase|g' /usr/src/app/zbot/conf/zbot.properties")
            script.sh("sed -i 's|\${zephyr.apitoken}|$script.secret|g' /usr/src/app/zbot/conf/zbot.properties")
            script.sh("sed -i 's|\${zephyr.zbotname}|$zbotSname|g' /usr/src/app/zbot/conf/zbot.properties")
            urlBase
                }
    }
    
    String runZbotShellScript() {
        script.sh('cp -r /usr/src/app/zbot /home/jenkins/agent/workspace')
        script.dir('/home/jenkins/agent/workspace/zbot') {
            script.sh('nohup /home/jenkins/agent/workspace/zbot/zbot_start.sh 2> 1')
            script.sh('sleep 5')
            String ipData = script.sh(script: 'awk \'/agentHostAndIp/{print $8}\' 1', returnStdout: true)
            script.echo(ipData)
            ipData = ipData.replaceAll( '\n' , ',' )
            ipData = ipData.replaceAll( 'login' , '' ).replaceAll( ',' , '' ).trim()
            String zbotname = 'QEABTZbot ( ' + ipData.trim() + ' )'
            zbotname
          }
    }
    String nowCreateVortex(String zbotnam, String urlBase) {
        String timeStamps = shiftLeftIndicatorHandle()
        String jsonPath   = testingConfiguration.jsonInputFilePath
        String wsPath = script.sh(script: 'pwd', returnStdout: true)
        wsPath = wsPath.trim() + '/'
        zbotname = zbotnam
        jname = 'ConduitJobRun'
        String text = urlBase + '/flex/services/rest/v3/automation/job/detail'
        if (jsonPath  == 'NA') {
            script.withCredentials([
                script.string(
                    credentialsId: "${testingConfiguration.credentialsId}", variable: 'secret'
                )
            ]) {               
                floc = wsPath + testingConfiguration.FLOC
                endDt = testingConfiguration.endDate
                startDt = testingConfiguration.startDate
                cyclName = testingConfiguration.cycleName
                if ( testingConfiguration?.shifteLftIndicator ) {
                    cyclname = cyclname + '_' + timeStamps.trim()
                }
                phName = testingConfiguration.folderName
                repoPath = testingConfiguration.testRepositoryPath
                pid = testingConfiguration.projectId
                rid = testingConfiguration.releaseId
                tid = testingConfiguration.folderId
                framew = testingConfiguration.automationFramework
                rloc = testingConfiguration.RLOC
                rloc = rloc.trim()
                rloc = wsPath + rloc
                aloc = rloc + '.zip'
                aloc = aloc.replaceAll( '\".zip' , '.zip\"' ).trim()
                aloc = aloc.replaceAll( '\"' , '' ).trim()
                String idStringPartOne = "curl -X POST -H \'Authorization: Bearer \'${script.secret}\' \' -H "
                String idStringPartOnes = "\'Content-Type: application/json\' -d"
                String idStringPartTwo = "\'{\"resultPath\":\"${floc}\",\"zbotAgentMachine\":\"${zbotname}\","
                String idStringPartTwos = "\"cycleEndDateStr\":\"${endDt}\",\"cycleStartDateStr\":\"${startDt}\",\""
                String idStringPartThree = "isDateStr\":true,\"cycleName\":\"${cyclName}\","
                String nidStringPartThree = "\"phaseName\":\"${phName}\",\"testRepositoryPath\":"
                String idStringPartThrees = "\"${repoPath}\",\"assignResultsTo\":\"-10\",\"isReuse\":true,"
                String idStringPartFour = "\"jobName\":\"${jname}\",\"jobDescription\":\"\","
                String idStringFours = "\"automationFramework\":\"${framew}\",\"projectId\":\"${pid}\",\"releaseId\":"
                String nidStringPartFours = "\"${rid}\",\"jobDetailTcrCatalogTreeId\":\"${tid}\"}\' ${text} "
                String newIdStringf = idStringPartOne + idStringPartOnes + idStringPartTwo
                String newIdStrings = idStringPartTwos + idStringPartThree + nidStringPartThree + idStringPartThrees
                String newIdStringt = idStringPartFour + idStringFours + nidStringPartFours
                String newIdString = newIdStringf + newIdStrings + newIdStringt
                id = script.sh(script: "${newIdString}", returnStdout: true)
                }
        }
        else {
            id = nowCreateVortexPartTwo(zbotnam, jsonPath, text)
        }
        script.sh('sleep 10')
        script.sh("rm -f ${aloc}")
        script.sh("java -jar ZipFileCreation.jar ${rloc}")
        if ( testingConfiguration?.robotToJunitConv ) {
            if ( testingConfiguration.robotToJunitConv == 'yes' ) {
                script.sh("java -jar RobotToJUnit.jar ${floc}")
            }
        }
        script.sh("java -jar argslevel.jar ${floc} ${aloc}")
        id
          }
    String nowCreateVortexPartTwo(String zbotnam, String jsonPath, String text) {
        String wsPath = script.sh(script: 'pwd', returnStdout: true)
        wsPath = wsPath.trim() + '/'
        jsonPath = wsPath + jsonPath
        script.withCredentials([
                script.string(
                    credentialsId: "${testingConfiguration.credentialsId}", variable: 'secret'
                )
            ]) {
            String executionRegion = testingConfiguration.testExecutionRegion
            String executionPhase = testingConfiguration.testPhase
            String folderName = executionRegion + '_' + executionPhase + '_PHNAME'
            String cycleName = executionRegion + '_' + executionPhase + '_CYCLNAME'
            String testRepositoryPath = executionRegion + '_' + executionPhase + '_REPOPATH'
            String folderId = executionRegion + '_' + executionPhase + '_TID'                       
            floc = geneFloc(jsonPath)
            endDt = geneEndDt(jsonPath)
            startDt = geneStartDt(jsonPath)
            cyclName = geneCyclName(jsonPath, cycleName)
            phName = genePhName(jsonPath, folderName)
            repoPath = geneRepoPath(jsonPath, testRepositoryPath)
            pid = genePid(jsonPath)
            rid = geneRid(jsonPath)
            tid = geneTid(jsonPath, folderId)
            framew = geneFramew(jsonPath)
            String idStringPartOne = "curl -X POST -H \'Authorization: Bearer \'${script.secret}\' \' -H "
            String idStringPartOnes = "\'Content-Type: application/json\' -d"
            String idStringPartTwo = "\'{\"resultPath\":\"${floc}\",\"zbotAgentMachine\":\"${zbotnam}\","
            String idStringPartTwos = "\"cycleEndDateStr\":\"${endDt}\",\"cycleStartDateStr\":\"${startDt}\",\""
            String idStringPartThree = "isDateStr\":true,\"cycleName\":\"${cyclName}\","
            String nidStringPartThree = "\"phaseName\":\"${phName}\",\"testRepositoryPath\":"
            String idStringPartThrees = "\"${repoPath}\",\"assignResultsTo\":\"-10\",\"isReuse\":true,"
            String idStringPartFour = "\"jobName\":\"${jname}\",\"jobDescription\":\"\","
            String idStringPartFours = "\"automationFramework\":\"${framew}\",\"projectId\":\"${pid}\",\"releaseId\":"
            String nidStringPartFours = "\"${rid}\",\"jobDetailTcrCatalogTreeId\":\"${tid}\"}\' ${text} "
            String newIdStringf = idStringPartOne + idStringPartOnes + idStringPartTwo
            String newIdStrings = idStringPartTwos + idStringPartThree + nidStringPartThree + idStringPartThrees
            String newIdStringt = idStringPartFour + idStringPartFours + nidStringPartFours
            String newIdString = newIdStringf + newIdStrings + newIdStringt
            id = script.sh(script: "${newIdString}", returnStdout: true)
            rloc = script.sh(script: "grep -Po '\"RLOC\": *\\K\"[^\"]*\"' ${jsonPath  }", returnStdout: true)
            rloc = rloc.trim()
            rloc = wsPath + rloc
            aloc = rloc + '.zip'
            aloc = aloc.replaceAll('\".zip' , '.zip\"' ).trim()
            aloc = aloc.replaceAll( '\"' , '' ).trim()
            id
                   }
    }
    String geneFloc(String jsonPath) {
        String wsPath = script.sh(script: 'pwd', returnStdout: true)
        wsPath = wsPath.trim() + '/'
        floc = script.sh(script: "grep -Po '\"FLOC\": *\\K\"[^\"]*\"' ${jsonPath}", returnStdout: true)
        floc = floc.replaceAll( '\"' , '' ).trim()
        floc = wsPath + floc
        floc
    }
    String geneEndDt(String jsonPath) {
        endDt = script.sh(script: "grep -Po '\"ENDDT\": *\\K\"[^\"]*\"' ${jsonPath}", returnStdout: true)
        endDt = endDt.replaceAll( '\"' , '' ).trim()
        endDt
    }
    String geneStartDt(String jsonPath) {
        String strtCmd = "grep -Po '\"STARTDT\": *\\K\"[^\"]*\"' ${jsonPath}"
        startDt = script.sh(script: "${strtCmd}", returnStdout: true)
        startDt = startDt.replaceAll( '\"' , '' ).trim()
        startDt
    }
    String geneCyclName(String jsonPath, String cycleName) {
        String timeStamps = shiftLeftIndicatorHandle()
        String cyclNameCommand = "grep -Po '\"${cycleName}\": *\\K\"[^\"]*\"' ${jsonPath}"
        cyclName = script.sh(script:"${cyclNameCommand}", returnStdout: true)
        cyclName = cyclName.replaceAll( '\"' , '' ).trim()
        if ( testingConfiguration?.shiftLeftIndicator ) {
            cyclName = cyclName + '_' + timeStamps.trim()
  }
 
        cyclName
    }
    String genePhName(String jsonPath, String folderName) {
        String phNameCommand = "grep -Po '\"${folderName}\": *\\K\"[^\"]*\"' ${jsonPath  }"
        phName = script.sh(script:"${phNameCommand}", returnStdout:true)
        phName = phName.replaceAll( '\"' , '' ).trim()
        phName
    }
    String geneRepoPath(String jsonPath, String testRepositoryPath) {
        String repoPathString = "grep -Po '\"${testRepositoryPath}\": *\\K\"[^\"]*\"' ${jsonPath  }"
        String repoPath = script.sh(script: "${repoPathString}", returnStdout: true)
        repoPath = repoPath.replaceAll( '\"' , '' ).trim()
        repoPath
    }
    String genePid(String jsonPath) {
        String pid = script.sh(script: "grep -Po '\"PID\": *\\K\"[^\"]*\"' ${jsonPath  }", returnStdout: true)
        pid = pid.replaceAll( '\"' , '' ).trim()
        pid
    }
    String geneRid(String jsonPath) {
        String rid = script.sh(script: "grep -Po '\"RID\": *\\K\"[^\"]*\"' ${jsonPath  }", returnStdout: true)
        rid = rid.replaceAll( '\"' , '' ).trim()
        rid
    }
    String geneTid(String jsonPath, String folderId) {
        String tidCommand = "grep -Po '\"${folderId}\": *\\K\"[^\"]*\"' ${jsonPath  }"
        String tid = (script.sh(script: "${tidCommand}", returnStdout: true))
        tid = tid.replaceAll( '\"' , '' ).trim()
        tid
    }
    String geneFramew(String jsonPath) {
        String frwCmd = "grep -Po '\"FRAMEW\": *\\K\"[^\"]*\"' ${jsonPath  }"
        String framew = script.sh(script: "${frwCmd}", returnStdout: true)
        framew = framew.replaceAll( '\"' , '' ).trim()
        framew
    }
    void executeVortex(String res, String urlBase) {
        script.withCredentials([
                script.string(
                    credentialsId: "${testingConfiguration.credentialsId}", variable: 'secret'
                )
            ]) {        
            urlBaseExecute = urlBase + '/flex/services/rest/v3/automation/schedule'
            String executePartOne = "curl -X POST -H \'Authorization: Bearer \'${script.secret}\' \' -H \'Content-Type:"
            String executePartTwo = " application/json\' -d \'{\"ids\":[${res}]}\' ${urlBaseExecute} "
            String executeCommand = executePartOne + executePartTwo
            script.sh(script: "${executeCommand}")           
            script.sh('sleep 2')
            boolean flag = true
            int i = 0
            while ( flag && i < 60 ) {
                String statusURLO = urlBase + '/flex/services/rest/latest/upload-file/'
                String statusURLT = 'automation/schedule/get-latest-job-progress?jobid=' + res
                String statusURL = statusURLO + statusURLT
                String comm = "curl -X GET -H \'Authorization: Bearer ${script.secret} \' ${statusURL} "
                String jobStatus = script.sh(script: "${comm}" , returnStdout: true)
                String status = (jobStatus.split(',')[2]).split(':')[1].replaceAll( '\"' , '' ).trim()
                script.echo(status)
                if ( status == 'Waiting' || status == 'in-progress' || status == 'new' )  {
                    script.sh('sleep 10')
                    script.echo(status)
                    i = i + 1
            } else {
                    script.echo(status)
                    flag = false
            }
            }
                 }
    }
    void deleteVortex(String res, String urlBase) {
        script.withCredentials([
                script.string(
                    credentialsId: "${testingConfiguration.credentialsId}", variable: 'secret'
                )
            ]) {          
            urlBaseDelete = urlBase + '/flex/services/rest/v3/automation/job/delete'
            String deletePartOne = "curl -X POST -H \'Authorization: Bearer \'${script.secret}\' \' -H \'Content-Type:"
            String deletePartTwo = " application/json\' -d \'{\"ids\":[${res}]}\' ${urlBaseDelete} "
            String deleteCommand = deletePartOne + deletePartTwo
            script.sh(script: "${deleteCommand}")
    }
    }
    String shiftLeftIndicatorHandle() {
        String timeStamp
        if ( testingConfiguration?.shiftLeftIndicator ) {
            if ( testingConfiguration.shiftLeftIndicator == 'true' ) {
                timeStamp = script.sh(script: 'date +\"%Y-%m-%d %T\" ', returnStdout: true)
                timeStamp = timeStamp.trim()
            }
        }
        timeStamp
     }
}
