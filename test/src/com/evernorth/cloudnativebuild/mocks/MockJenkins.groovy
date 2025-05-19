package com.evernorth.cloudnativebuild.mocks

import com.cigna.mocks.MockScript
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.pipeline.ShellModuleUtil
import com.evernorth.cloudnativebuild.service.PipelineStateManager

class MockJenkins extends MockScript {
    MockJenkins() {
        mockShellCommand = new MockShellCommand()
        mockShellCommand.createSimulatedCommands()
    }

    def steps = this

    MockShellCommand mockShellCommand
    boolean debugMode

    void echo(def message) {
        if (message) {
            if (debugMode) {
                println(message?.toString())
            }
            consoleMessages.add(message)
        }
    }
    // spy support
    int stashCount = 0
    int unStashCount = 0
    List<String> consoleMessages = []
    def writtenFiles = [:]
    int writeFileCount = 0
    def shellCommands = []
    def creds = []
    def checkpoints = []
    int checkOutCount = 0

    static def podTemplate(Map args, Closure code) {
        code()
    }
    def stage = {
        String s,
        Closure code ->
            try {
                if (debugMode) {
                    println("\n=-=-=-=-=-=-=-=-=-=-= BEGIN STAGE $s =-=-=-=-=-=-=-=-=-=-=")
                }
                code()
            } catch (ex) {
                if (debugMode) {
                    println("Exception in stage '$s': $ex")
                }
                throw ex
            } finally {
                if (debugMode) {
                    println("=-=-=-=-=-=-=-=-=-=-=- END STAGE $s -=-=-=-=-=-=-=-=-=-=-=\n")
                }
            }
    }

    def kubernetes = {
        Map m,
        Closure code -> code()
    }

    static def node(String label,
                    Closure code) {
        code()
    }

    static def node(Closure code) {
        code()
    }

    def error(String string) {
        if (debugMode) {
            println("ERROR: $string")
        }
    }

    def fileExists(String string) {
        if (debugMode) {
            println("Checking if fileExists: $string")
        }
        return (readFileResults as Map)?.containsKey(string)
    }


    Expando exp = new Expando()

    def httpRequest(Map map) {
        exp.getContent = { return 'SUCCESS' }
        exp.getStatus = { return 200 }
        return exp
    }

    def withCredentials(List args, Closure closure) {
        def delegate = [:]
        for (arg in args) {
            delegate[arg['cred_var']] = 'sometoken'
            this.creds.add(arg)
        }
        closure.delegate = delegate
        closure()
    }

    void checkpoint(String checkPointName) {
        if (debugMode) {
            println("Saving Checkpoint $checkPointName")
        }
        checkpoints.add(checkPointName)
    }


    static def usernamePassword(Map args) {
        return args
    }

    static def string(def m) {
        return m
    }

    static def withEnv(List args, Closure closure) {
        closure()
    }

    def sh(String script, Boolean returnStdout = false) {
        this.shellCommands.add(script)
        if (debugMode) {
            println("script.sh: $script")
        }
    }

    def sh(LinkedHashMap map) {
        def result = mockShellCommand.simulateShellResponse(map.get('script')?.toString()?.trim())
        if (map.get('returnStdout')) {
            return result.toString()
        }
        if (map.get('script')) {
            this.shellCommands.add(map.get('script'))
        }
        return null
    }

    def checkout(def scm) {
        checkOutCount++
    }

    def stash(def options) {
        if (options.name == "bad") {
            throw new IllegalArgumentException("Bad stuff")
        }
        if (debugMode) {
            println("Stashing... ${options.name}")
        }
        this.stashCount++
    }

    def unstash(def options) {
        if (options.name == "bad") {
            throw new IllegalArgumentException("Bad stuff")
        }
        if (debugMode) {
            println("unstashing... ${options.name}")
        }
        this.unStashCount++
    }
    def credVariable = "mycred"
    def defaultModules = [
        [contractName: "PREFLIGHT_CHECK", buildStage: "PRE_STASH", commandPrefix: "", commandName: "validatetf", image: "docker-dev.artifactory-dev.express-scripts.com/devops/calculator-springpcf-docker:38"],
        [contractName: "BUILD", commandPrefix: "", commandName: "mvn.sh", image: "docker-dev.artifactory-dev.express-scripts.com/devops/maven:cn1"],
        [contractName: "PACKAGE", commandName: "cnp-build-package-docker.sh", subCommand: "create", image: "docker-dev.artifactory-dev.express-scripts.com/kubernetes/cnp-docker-k8s:0.0.5"],
        [contractName: "PUBLISH", commandPrefix: "", commandName: "artifact.sh", image: "docker-dev.artifactory-dev.express-scripts.com/devops/maven:cn2"],
        [contractName: "PROVISION", commandName: "cnp-provision-terraform.sh", image: "docker-dev.artifactory-dev.express-scripts.com/devops/openshift:latest", moduleName: "cnp-provision-terraform"],
        [contractName: "DEPLOY", commandPrefix: "", commandName: "oc-deploy.sh", image: "docker-dev.artifactory-dev.express-scripts.com/devops/openshift:latest"]
    ]
    

    def booleanParam(def args) {

    }

    def parameters(def args) {

    }

    def properties(def args) {

    }
    def params = [:]

    


    // allows deep mock of get cause
    String causeShortDescription = 'upstream description'
    String causeUserName = ""
    String causeUserId = "userid"

    def getCause(def cause = null) {
        return [
            cause           : [:],
            userName        : causeUserName,
            userId          : causeUserId,
            getUserId       : { return causeUserId },
            shortDescription: causeShortDescription
        ]
    }

    static def input(def options) {
        if (options?.message == "ABORT") {
            throw new Exception("Aborted")
        }
        def inputExpando = new Expando()
        return inputExpando
    }

    static def timeout(def options, Closure code) {
        def timeoutExpando = new Expando()
        code()
        if (options?.time == 2) {
            throw new Exception("Time out")
        }
        return timeoutExpando
    }

    Map<String, String> readFileResults = [:]

    void addReadFileResult(String fileName, String resourceFileName = fileName) {
        readFileResults.put(fileName, new File("test/resources/${resourceFileName}").text)
    }

    void addReadFileResultFromString(String fileName, String fileContents) {
        readFileResults.put(fileName, fileContents)
    }

    def readFile(def options) {
        String key
        if (options instanceof Map && options?.file) {
            key = options.file as String
        } else {
            key = options as String
        }
        if (!readFileResults.containsKey(key)) {
            addReadFileResult(key)
        }
        if (!readFileResults?.isEmpty() && key != null) {
            return readFileResults.get(key)
        }
        println("File not found. Use MockJenkins setReadFileResult and pass name of file in test/resources/")
        return null
    }

    void writeFile(def options) {
        writtenFiles[options.file] = options.text
        writeFileCount++
        if (debugMode) {
            println("Writing file: ${options.file} with content: ${options.text}")
        }
    }

    // ---------  mock of DSL functions in vars
    def build(Map properties = [:], Map options = [:]) {
        inspectValue = "build@123456"
        ShellModuleUtil.deferExecuteShellModule(this
            , mockGlobalModuleManager, properties, options, ShellModuleUtil.findScheduleNameFromClosure())
    }

    def withModule(String moduleName, Closure code) {
        PipelineUtils.executeWithModule(this, moduleName, this.mockGlobalModuleManager, code)
    }
    PipelineStateManager mockGlobalModuleManager = null

    def deploy(Map properties = [:], Map options = [:], toDefer = false) {
        inspectValue = "deploy@123456"
        ShellModuleUtil.deferExecuteShellModule(this, this.mockGlobalModuleManager, properties, options, ShellModuleUtil.findScheduleNameFromClosure())
    }

    def cutover(Map properties = [:], Map options = [:], toDefer = false) {
        inspectValue = "cutover@123456"
        ShellModuleUtil.deferExecuteShellModule(this, this.mockGlobalModuleManager, properties, options, ShellModuleUtil.findScheduleNameFromClosure())
    }

    def release(Map properties = [:], Closure releaseSteps) {
        inspectValue = "release@12234"
        PipelineUtils.executeReleaseClosure(this, properties, mockGlobalModuleManager, releaseSteps)
    }

    def preRelease(Closure releaseSteps) {
        inspectValue = "preRelease@12234"
        PipelineUtils.executePreReleaseClosure(this, this.mockGlobalModuleManager, releaseSteps)
    }

    def runScript(String script, String imageUrl = "") {
        inspectValue = "runScript@12234"
        ShellModuleUtil.deferExecuteShellModule(this, this.mockGlobalModuleManager, [dockerImage: imageUrl, script: script], [:], ShellModuleUtil.findScheduleNameFromClosure())
    }

    void awaitApproval(Map properties = [:]) {
        inspectValue = "awaitApproval@12234"
        ShellModuleUtil.deferExecuteShellModule(this, this.mockGlobalModuleManager, properties)
    }

    def release(Map properties = [:], Map options = [:]) {
        echo("This release syntax is no longer supported. Please use the release closure syntax described here: https://confluence.express-scripts.com/display/CNP/CNP+Releases")
    }

    def emptyDirWorkspaceVolume(def v) {
        return []
    }

    def static readJSON(def map) {
        return [uri: "https://artifactory.express-scripts.com/reponame/path"]
    }

    def gituser = "gituser"
    def gitpass = "gitpass"

    private String inspectValue = "MockJenkins@123445"

    String inspect() {
        return inspectValue
    }

}