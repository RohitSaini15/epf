package com.cigna.common.utils


import com.cigna.state.PipelineStateContext

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ACE .bar file builder
 */
class BarBuilder {
    protected Object script
    protected PipelineStateContext psc
    protected Map<String, Object> config = [:]
    protected String containerName = null
    protected String barFolder = null
    protected String createBarCmd = null
    protected String showBarCmd = null
    protected String cmdResponsesUrl = null

    // map to hold all the project resources found in this repo
    protected Map<String, Map> resources = [:]
    // files that changed in this git commit
    protected List<String> changedFiles = []

    // really just the .bar filename suffix (ie, foo-<extraArgs>.bar)
    // This should be renamed to barNameSuffix
    protected String extraArgs = ''

    protected String commandArgs = ''
    // force all to be changed
    protected boolean forceAllChanged = false
    // number of resources that have changes
    protected int numChangedResources = 0
    // number of .bar files created
    protected int numBarFilesCreated = 0

    // constructor
    BarBuilder(PipelineStateContext psc, Object script, Map<String, Object> config, String containerName,
               String barFolder, String createBarCmd, String showBarCmd, String cmdResponsesUrl) {
        this.psc = psc
        this.script = script
        this.config = config
        this.containerName = containerName
        this.barFolder = barFolder
        this.createBarCmd = createBarCmd
        this.showBarCmd = showBarCmd
        this.cmdResponsesUrl = cmdResponsesUrl
    }

    void showProperties() {
        script.echo(
            '*** BarBuilder properties ***\n'
                + "*   containerName: ${containerName}\n"
                + "*       barFolder: ${barFolder}\n"
                + "*    createBarCmd: ${createBarCmd}\n"
                + "*      showBarCmd: ${showBarCmd}\n"
                + "* cmdResponsesUrl: ${cmdResponsesUrl}\n"
                + '***'
        )
    }

    @SuppressWarnings(['FactoryMethodName'])
    int createBarFiles() {
        commandArgs = config.containsKey('commandArgs') ? " ${config.commandArgs}" : ''
        extraArgs = config?.extraArgs
        if ((extraArgs == null) || extraArgs.isEmpty()) {
            // what time zone should we use for below?
            LocalDateTime now = LocalDateTime.now()
            String timestamp = now.format(DateTimeFormatter.ofPattern('yyyyMMddHHmmss'))
            extraArgs = "${script.env.GIT_COMMIT_SHORT}_${timestamp}_${script.env.BUILD_NUMBER}"
        }

        forceAllChanged = config?.forceAllChanged == true

        // create folder for bar files
        psc.podSelector.select(psc, containerName, Utils.cloud(config)) {
            script.sh(script: "mkdir -p ${barFolder}")
        }

        boolean autoDiscover = config?.autoDiscover == true
        if (autoDiscover) {
            script.echo('Automagically discovering resources to build.')
            // find all the changed files in this commit
            findChangedFiles()
            // next parse all the .project files in the repo to discover dependencies
            findAndParseProjectFiles()
            resolveDependencies()
            resources.each { key, resource ->
                numChangedResources += resource.changed ? 1 : 0
            }
            showResourcesMapAndChangedFiles('Resources and Changed Files')
            // determine what changed from git and which .bar files actually need recreating
            buildBarFiles()
        } else {
            String resourceName = config?.resourceName
            String resourceType = config?.resourceType
            String resourcePath = config?.resourcePath
            String resourceDependencies = config?.resourceDependencies
            script.echo("Building ${resourceType} ${resourceName}.")
            // if the resource is given in the Jenkinsfile, then assume it needs building
            numChangedResources = 1
            // create .bar file from info in Jenkinsfile
            createResource(resourceName, resourceType, resourcePath, resourceDependencies)
        }

        if (numChangedResources > 0) {
            psc.podSelector.select(psc, containerName, Utils.cloud(config)) {
                script.sh(script: "ls -l ${barFolder}")
            }
            List<Map<String, Object>> barfiles = script.findFiles(glob: "${barFolder}/*.bar")
            for (bf in barfiles) {
                showBarFileContents(bf.path)
                numBarFilesCreated += isValidBarFile(bf.path) ? 1 : 0
            }
            if (numBarFilesCreated == 0) {
                script.echo('ERROR: No valid .bar files were created!')
                throw new FailedAce('ERROR: No valid .bar files were created!')
            }
        } else {
            script.echo('INFO: None of the changed files required any .bar files to be recreated.')
        }

        // return numBarFilesCreated
        numBarFilesCreated
    }

    // used only to show the contents of the resources map and changed files list
    void showResourcesMapAndChangedFiles(String title) {
        String output = "\n* * * ${title} * * *\n"
        output += '-[ RESOURCES ]-\n'
        resources.each { key, resource ->
            output += "  projectFile: ${resource.projectFile}\n"
            output += "   folderName: ${resource.folderName}\n"
            output += "  projectName: ${resource.projectName}\n"
            output += " resourceType: ${resource.resourceType}\n"
            output += " dependencies: ${resource.dependencies}\n"
            output += "      changed: ${resource.changed}\n"
            output += "        built: ${resource.built}\n"
            output += '---\n'
        }
        output += '-[ CHANGED FILES ]-\n'
        changedFiles.each {
            output += " ${it}\n"
        }
        output += "* * * ${title} * * *\n"
        script.echo(output)
    }

    void findChangedFiles() {
        String stdout
        psc.podSelector.select(psc, containerName, Utils.cloud(config)) {
            stdout = script.sh(
                script: "git show --pretty= --name-only ${script.env.GIT_COMMIT}",
                returnStdout: true
            )
        }
        changedFiles = stdout.split('\n')
        script.echo("Changed Files:\n${changedFiles}")
    }

    void findAndParseProjectFiles() {
        List<Map<String, Object>> files = script.findFiles(glob: '**/.project')
        for (f in files) {
            if (f.directory) {
                script.echo("!!! Skipping ${f.path} (it is a directory) !!!")
            } else if (f.path.contains('.metadata')) {
                script.echo("!!! Skipping ${f.path} (it is metadata) !!!")
            } else {
                String[] xml = readXmlFile(f.path)
                if (validProjectFile(xml)) {
                    addProjectDataToMap(f.path, xml)
                } else {
                    script.echo("!!! Skipping ${f.path} (not a valid .project file) !!!")
                }
            }
        }
    }

    boolean validProjectFile(String[] xml) {
        // make sure this .project file contains one or more <buildCommand> tags
        int numTags = 0
        xml.each {
            numTags += (it.contains('<buildCommand>')) ? 1 : 0
        }
        // return
        numTags > 0
    }

    // this parses a .project file and adds relevant data to the resources map
    void addProjectDataToMap(String projectFile, String[] xml) {
        // get resource type
        String resourceType = getResourceType(xml)
        script.echo("Processing ${projectFile} ...")
        // get folder name
        String folderName = projectFile.replace('./', '').replace('/.project', '')
        // get project name
        String projectName = 'not-found'
        xml.each {
            if (it.contains('<name>') && !it.contains('<name>com.ibm.')) {
                projectName = it.replace('<name>', '').replace('</name>', '').trim()
            }
        }
        // get project dependencies
        String dependencies = ','
        xml.each {
            if (it.contains('<project>')) {
                dependencies += it.replace('<project>', '').replace('</project>', '').trim()
                dependencies += ','
            }
        }

        // add project data to resources map
        resources[projectFile] = [
            projectFile : "${projectFile}",
            folderName  : "${folderName}",
            projectName : "${projectName}",
            resourceType: "${resourceType}",
            dependencies: "${dependencies}",
            changed     : hasChangedFiles(folderName),
            built       : false,
        ]
    }

    String getResourceType(String[] xml) {
        // get <nature> tags
        String natureTags = ''
        xml.each {
            natureTags += it.contains('<nature') ? it : ''
        }
        String resourceType = 'msgflow'
        if (natureTags == '') {
            throw new FailedAce('ERROR: no <nature> tags found!')
        } else if (natureTags.contains('.applicationNature')) {
            resourceType = 'application'
        } else if (natureTags.contains('.sharedLibraryNature')) {
            // a shared library will always be a stand-alone .bar file
            resourceType = 'shared-library'
        } else if (natureTags.contains('.libraryNature')) {
            // a static library will be included in the application .bar file that references it
            resourceType = 'library'
        } else if (natureTags.contains('.msetnature')) {
            resourceType = 'msgset'
        }
        // return resourceType
        resourceType
    }

    // determine if this resource has and changed files in the current git commit
    boolean hasChangedFiles(String folderName) {
        boolean foundChangedFiles = forceAllChanged
        if (foundChangedFiles == false) {
            changedFiles.each {
                if ("^${it}".contains("^${folderName}/")) {
                    foundChangedFiles = true
                }
            }
        }
        // return foundChangedFiles
        foundChangedFiles
    }

    void resolveDependencies() {
        resources.each { key, resource ->
            // find all resources that have no children (leaf nodes)
            if ((resource.dependencies == ',') && (resource.resourceType != 'shared-library')) {
                updateParentsChangedStatus(resource.projectName, resource.changed)
            }
        }
    }

    void updateParentsChangedStatus(String childProjectName, boolean childChanged) {
        resources.each { key, resource ->
            if (resource.dependencies.contains(",${childProjectName},")) {
                resources[key].changed = resources[key].changed || childChanged
                // recurse to mark this resource's parents as changed
                updateParentsChangedStatus(resources[key].projectName, resources[key].changed)
            }
        }
    }

    int buildBarFiles() {
        int numBuilt = 0
        String allProjectFolders = config.get('useDependencies', false) ? getAllProjectFolders() : null
        resources.each { key, resource ->
            if ((resource.resourceType == 'application') || (resource.resourceType == 'shared-library')) {
                if (resource.changed && !resource.built) {
                    createResource(resource.projectName, resource.resourceType, resource.folderName, allProjectFolders)
                    resources[key].built = true
                    numBuilt++
                }
            }
        }
        // return numBuilt
        numBuilt
    }

    String getAllProjectFolders() {
        // gather all .project folders in a space separated list
        String allFolders = ''
        resources.each { key, resource ->
            allFolders += ' ' + resource.folderName
        }
        // return allFolders
        allFolders
    }

    @SuppressWarnings(['FactoryMethodName'])
    boolean createResource(String resourceName, String resourceType, String resourcePath, String resourceDependencies) {
        String barFilename = "${resourceName}-${extraArgs}.bar"

        script.echo(
            "Building resource name ${resourceName}"
                + ", type ${resourceType}"
                + ", path ${resourcePath}"
                + ", barfile ${barFilename}"
        )

        def includeDefaultFlags = config.get('includeDefaultFlags', true)
        if (!includeDefaultFlags) {
            script.echo("Disabling default flags, NOTE you will need to include all necessary command line arguments, as none are defaulted")
        }
        // mgsicreatebar options
        //  not used in w1 built command line
        String options = "${commandArgs ? "${commandArgs} " : ''}${includeDefaultFlags? '-configuration . -data .' : ''}"
        if (resourceDependencies) {
            options += " -p ${resourceDependencies}"
        }

        options += " -b ${barFolder}/${barFilename}"

        def additionalArgs = includeDefaultFlags ? ' -deployAsSource' : ''
        switch (resourceType) {
            case 'application':
                options += " -a ${resourcePath}${additionalArgs}"
                break
            case 'shared-library':
                options += " -l ${resourcePath}${additionalArgs}"
                break
            case 'library':
                options += " -l ${resourcePath}${additionalArgs}"
                break
            case 'msgset':
                options += " -o ${resourcePath}/messageSet.mset"
                break
            case 'msgflow':
                options += " -o ${resourcePath}/${resourceName}.msgflow"
                break
            default:
                throw new FailedAce("ERROR: Invalid resourceType ${resourceType}!"
                    + ' Valid values are: application, library, msgflow, msgset')
        }
        String rc
        psc.podSelector.select(psc, containerName, Utils.cloud(config)) {
            rc = script.sh(script: "$createBarCmd $options", returnStdout: true)
        }

        if ((rc == null) || !rc.contains('Command completed successfully')) {
            script.echo("ERROR: Failed to create $barFilename! $rc")
            script.echo("ERROR: See $cmdResponsesUrl for details.")
            return false
        }
        script.echo("Successfully created ${barFilename}: $rc")
        // return true
        true
    }

    String[] readXmlFile(String xmlfile) {
        String stringXmlFile = script.readFile(xmlfile)
        String[] arrayXmlFile = stringXmlFile.split('\n')
        // return arrayXmlFile
        arrayXmlFile
    }

    void showBarFileContents(String barfile) {
        script.echo("***\n*** Contents of ${barfile}\n***")
        psc.podSelector.select(psc, containerName, Utils.cloud(config)) {
            script.sh("${showBarCmd} -b ${barfile} -r")
        }
    }

    boolean isValidBarFile(String barfile) {
        if (barfile == null) {
            script.echo('ERROR: barfile is null!')
            return false
        }
        String output
        output = psc.podSelector.select(psc, containerName, Utils.cloud(config)) {
            script.sh(script: "${showBarCmd} -b ${barfile} -r", returnStdout: true)
        }
        if ((output == null) || !output.contains('Successful command completion')) {
            script.echo("ERROR: Show bar file for ${barfile} failed!")
            script.echo("ERROR: See ${cmdResponsesUrl} for details.")
            if (output) {
                script.echo("REASON: $output")
            }
            return false
        }
        String[] lines = output.split('\n')
        int start = 0
        int end = 0
        lines.eachWithIndex { item, index ->
            if (item.contains("${barfile}:")) {
                start = index
            } else if (item.contains('Successful command completion')) {
                end = index
            }
        }
        if (start <= 0) {
            script.echo('ERROR: Could not find start of output from show bar file command!')
            return false
        }
        if (end <= 0) {
            script.echo('ERROR: Could not find end of output from show bar file command!')
            return false
        }
        if ((end - start) < 5) {
            script.echo("ERROR: File ${barfile} is empty or invalid!")
            return false
        }
        // return true
        true
    }
}

class FailedAce extends Exception {
    FailedAce(String message) {
        super(message)
    }
}