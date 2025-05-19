package com.evernorth.cloudnativebuild.mocks

class MockShellCommand {
    static String bashCommandNotFound(String command){
        return "bash: $command: command not found"
    }
    static final String THROW = "THROW"
    static final String NO_RESULT = "NO RESULT"
    def simulatedCommands = [:]

    void createSimulatedCommands(){
        def map= [:]
        map.put("build_mvn.sh clean install -B --pomfile=mydir/mypom.xml",
                "{\"commandOutput\":\"Build SUCCESS\",\"commandSuccess\":\"true\",\"errors\":null,\"artifactPath\":\"/home/jenkins/devops-testapps-SpringPcf/target\"}"
        )
        map.put("preflight_check_validategitrepo.sh", "{\"commandOutput\":\"preflight check failed\",\"commandSuccess\":\"true\",\"errors\":[\"Incorrect branch protection configured\"]} ")
        map.put("preflight_check_validatetf.sh", "{\"commandOutput\":\"preflight check completed successfully\",\"commandSuccess\":\"true\",\"errors\":null}")
        map.put("sed -n 's/^commandResult: //p' package_log.txt","SUCCESS")
        map.put("\"sed -n 's/^commandOutput: //p' package_log.txt\"","some output")
        map.put("cause-an-error '{}'",THROW)
        map.put("cat  2> /dev/null", "")
        simulatedCommands.putAll(map)
    }

    void addModuleSimulation(String command, String moduleName=command, String resultsFile="cnp-success-results.json"){
        String resultsFileName=moduleName.replace(".sh","")+ "-results.json"
        String findCommand = "find . -name $resultsFileName -type f"
        String catCommand = "cat $resultsFileName 2> /dev/null"
        simulatedCommands.put((command),"")
        simulatedCommands.put((findCommand),"cnp-validate-images-results.json")
        simulatedCommands.put((catCommand),(new File("test/resources/${resultsFile}").text))
    }

    String simulateShellResponse(String command){
        if(!command) return ""

        def result = simulatedCommands.find {cmd -> cmd.toString().startsWith(command.trim())}
        switch (result?.value){
            case null:
                return bashCommandNotFound(command)
            case THROW:
                // https://www.jenkins.io/doc/pipeline/steps/workflow-durable-task-step/
                // if sh ends with non-zero return code exception will be thrown
                throw new Exception("sh exception")
            case NO_RESULT:
                return ""
        }
        return result.value
    }
}
