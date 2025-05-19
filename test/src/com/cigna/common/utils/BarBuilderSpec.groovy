package com.cigna.common.utils

import com.cigna.SinglePodTest

class BarBuilderSpec extends SinglePodTest {
    protected static final String CREATE_BAR_CMD = 'create-bar.sh'
    protected static final String SHOW_BAR_CMD = 'show-bar.sh'
    protected static final String CMD_RESPONSES_URL = 'https://docs.ibm.com/cmd-responses/'
    protected static final String BAR_FOLDER = 'bar-folder'
    protected static final String CONTAINER_NAME = 'container-name'

    def cloudName = 'test-cloud'

    def remoteConfigs = [
            [
                    url: "https://github.sys.cigna.com/somecool_project/super_cool.git"
            ]
    ]

    def setup() {
        explicitlyMockPipelineStep('findFiles')
        explicitlyMockPipelineStep('override')
        getPipelineMock("scm.getProperty")('userRemoteConfigs') >> remoteConfigs
        initScriptAndPsc()
    }

    def """When constructor is called, then class is instantiated correctly."""() {
        given:
        BarBuilder bb = new BarBuilder(psc, script, [:], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        when:
        bb.showProperties()

        then:
        assert bb.class == BarBuilder
        1 * getPipelineMock("echo")(_) >> { _arguments ->
            assert _arguments[0].contains('BarBuilder properties') == true
            assert _arguments[0].contains("containerName: ${CONTAINER_NAME}") == true
            assert _arguments[0].contains("barFolder: ${BAR_FOLDER}") == true
            assert _arguments[0].contains("createBarCmd: ${CREATE_BAR_CMD}") == true
            assert _arguments[0].contains("showBarCmd: ${SHOW_BAR_CMD}") == true
            assert _arguments[0].contains("cmdResponsesUrl: ${CMD_RESPONSES_URL}") == true
        }
    }

    // void createBarFiles()
    def """When extraArgs are not specified, then the default value is used."""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        def timestampRegEx = '20[0-9][0-9][0-1][0-9][0-3][0-9][0-2][0-9][0-5][0-9][0-5][0-9]'
        simulatePodTemplate(psc, cloudName)
        when:
        bb.createBarFiles()

        then:
        assert bb.extraArgs ==~ /${script.env['GIT_COMMIT_SHORT']}_${timestampRegEx}_${script.env['BUILD_NUMBER']}/
        thrown(FailedAce)
    }

    // void createBarFiles()
    def """When extraArgs are specified, then the default value is not used."""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [extraArgs: 'hello-world',], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        simulatePodTemplate(psc, cloudName)
        when:
        bb.createBarFiles()

        then:
        assert bb.extraArgs == 'hello-world'
        thrown(FailedAce)
    }

    // void createBarFiles()
    def """When autoDiscover is false, then only 1 resource built."""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [
                autoDiscover        : false,
                resourceName        : 'TESTING_App',
                resourceType        : 'application',
                resourcePath        : 'TESTING',
                resourceDependencies: 'TESTING',
                extraArgs           : 'testing',
        ], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        simulatePodTemplate(psc, cloudName)
        when:
        bb.createBarFiles()

        then:
        1 * getPipelineMock("echo")(
                'Building resource name ' + bb.config.resourceName
                        + ', type ' + bb.config.resourceType
                        + ', path ' + bb.config.resourcePath
                        + ', barfile ' + bb.config.resourceName + '-' + bb.config.extraArgs + '.bar'
        )
        thrown(FailedAce)
    }

    def """When autoDiscover is true, then multiple resources may be built."""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [autoDiscover: true,], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        simulatePodTemplate(psc, cloudName)
        when:
        getPipelineMock("sh")(_) >> 'git/commit\nchanged/files\nmock'
        bb.createBarFiles()

        then:
        1 * getPipelineMock("echo")("Automagically discovering resources to build.")
        assert bb.config.autoDiscover == true
    }

    def """When showResourcesMapAndChangedFiles is called, then correct info is shown"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        bb.resources = [
                'key1': [
                        projectFile : 'foo/.project',
                        folderName  : 'foo',
                        projectName : 'FOO',
                        resourceType: 'application',
                        dependencies: ',',
                        changed     : true,
                        built       : false,
                ],
        ]
        bb.changedFiles = [
                'foo',
                'bar',
        ]
        simulatePodTemplate(psc, cloudName)
        when:
        bb.showResourcesMapAndChangedFiles('testing 123')

        then:
        1 * getPipelineMock("echo")(_) >> { _arguments ->
            assert _arguments[0].contains('testing 123') == true
            assert _arguments[0].contains('-[ RESOURCES ]-') == true
            assert _arguments[0].contains('projectFile: foo/.project') == true
            assert _arguments[0].contains('folderName: foo') == true
            assert _arguments[0].contains('projectName: FOO') == true
            assert _arguments[0].contains('resourceType: application') == true
            assert _arguments[0].contains('dependencies: ,') == true
            assert _arguments[0].contains('changed: true') == true
            assert _arguments[0].contains('built: false') == true
            assert _arguments[0].contains('-[ CHANGED FILES ]-\n foo\n bar\n') == true
        }
    }

    // void findChangedFiles()
    def """When findChangedFiles is called, then all git commit changed files are detected"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        simulatePodTemplate(psc, cloudName)
        when:
        bb.changedFiles = []
        getPipelineMock("sh")(_) >> gitShow
        bb.findChangedFiles()

        then:
        assert bb.changedFiles == expected

        where:
        gitShow                  || expected
        'hello/world/foo.txt'    || ['hello/world/foo.txt']
        'hello\nworld\nfoo\nbar' || ['hello', 'world', 'foo', 'bar']
    }

    // void findAndParseProjectFiles()
    def """When findAndParseProjectFiles is called, then all .project files are found and processed"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        def projectFiles = [[
                                    name     : '.project',
                                    directory: true,
                                    path     : '/hello/.project',
                            ], [
                                    name     : '.project',
                                    directory: false,
                                    path     : '/hello/.metadata/.project',
                            ], [
                                    name     : '.project',
                                    directory: false,
                                    path     : '/hello/world1/.project',
                            ]]
        explicitlyMockPipelineStep("addProjectDataToMap")
        simulatePodTemplate(psc, cloudName)
        when:
        bb.findAndParseProjectFiles()

        then:
        1 * getPipelineMock("findFiles")(_) >> projectFiles
        for (f in projectFiles) {
            if (f.directory) {
                0 * getPipelineMock("readFile")(f.path)
                1 * getPipelineMock("echo")("!!! Skipping ${f.path} (it is a directory) !!!")
            } else if (f.path.contains('.metadata')) {
                0 * getPipelineMock("readFile")(f.path)
                1 * getPipelineMock("echo")("!!! Skipping ${f.path} (it is metadata) !!!")
            } else {
                1 * getPipelineMock("readFile")(f.path) >> '<buildCommand>foo</buildCommand>\n<nature>.applicationNature</nature>'
                //thrown(FailedAce)
            }
        }
    }

    // boolean validProjectFile(String filename)
    def """When .project file is valid, then validProjectFile returns true"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        simulatePodTemplate(psc, cloudName)
        when:
        def result = bb.validProjectFile([xml] as String[])

        then:
        assert result == expectedResult

        where:
        xml                                || expectedResult
        '<foo>bar</foo>'                   || false
        '<buildCommand>foo</buildCommand>' || true

    }

    def """When addProjectDataToMap is called, then a new resource should be added correctly to map"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        String[] xml = [
                '<?xml version="1.0" encoding="UTF-8"?>',
                '<projectDescription>',
                '    <name>TEST_NAME</name>',
                '    <projects>',
                '        <project>TEST_DEP_1</project>',
                '        <project>TEST_DEP_2</project>',
                '        <project>TEST_DEP_3</project>',
                '    </projects>',
                '    <buildSpec>',
                '        <buildCommand>',
                '            <name>com.ibm.etools.mft.mapping.builder.hellobuilder</name>',
                '            <arguments>',
                '       	 </arguments>',
                '        </buildCommand>',
                '        <buildCommand>',
                '            <name>com.ibm.etools.mft.mapping.builder.worldbuilder</name>',
                '            <arguments>',
                '       	 </arguments>',
                '        </buildCommand>',
                '    </buildSpec>',
                '    <natures>',
                '        <nature>com.ibm.etools.mft.bar.ext.barnature</nature>',
                '        <nature>com.ibm.etools.msgbroker.tooling.messageBrokerProjectNature</nature>',
                '        <nature>com.ibm.etools.msgbroker.tooling.applicationNature</nature>',
                '    </natures>',
                '</projectDescription>',
        ]
        String projectFile = '/hello/world/.project'
        def expected = [
                '/hello/world/.project': [
                        projectFile : '/hello/world/.project',
                        folderName  : '/hello/world',
                        projectName : 'TEST_NAME',
                        resourceType: 'application',
                        dependencies: ',TEST_DEP_1,TEST_DEP_2,TEST_DEP_3,',
                        changed     : false,
                        built       : false,
                ]
        ]
        simulatePodTemplate(psc, cloudName)
        when:
        int sizeBefore = bb.resources.size()
        bb.addProjectDataToMap(projectFile, xml)
        int sizeAfter = bb.resources.size()

        then:
        assert sizeBefore == 0
        assert sizeAfter == 1
        assert bb.resources == expected
    }

    // String getResourceType(String projectFile)
    def """When .project file contains applicationNature, then resource type should be application"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        simulatePodTemplate(psc, cloudName)
        when:
        def resourceType = bb.getResourceType([xml] as String[])

        then:
        assert resourceType == expectedResourceType

        where:
        xml                                                                     || expectedResourceType
        '<nature>com.ibm.etools.msgbroker.tooling.applicationNature</nature>'   || 'application'
        '<nature>com.ibm.etools.msgbroker.tooling.sharedLibraryNature</nature>' || 'shared-library'
        '<nature>com.ibm.etools.msgbroker.tooling.libraryNature</nature>'       || 'library'
        '<nature>com.ibm.etools.msgbroker.tooling.msetnature</nature>'          || 'msgset'
        '<nature>com.ibm.etools.msgbroker.tooling.fooNature</nature>'           || 'msgflow'
    }

    // boolean hasChangedFiles()
    def """When a folder contains changed files, then return true"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        bb.changedFiles = [
                'parent1/hello/world1.txt',
                'parent1/hello/world2.txt',
                'parent2/hello/world1.txt',
                'parent2/hello/world2.txt',
        ]
        simulatePodTemplate(psc, cloudName)
        when:
        boolean rc = bb.hasChangedFiles(folderName)

        then:
        assert rc == expected

        where:
        folderName      || expected
        'foo/bar'       || false
        'parent1/hello' || true
        'parent1/foo'   || false
    }

    // void resolveDependencies()
    def """When resolveDependencies is called, then correct dependencies are found"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        bb.resources = [
                'key1': [
                        folderName  : '/hello/world',
                        projectName : 'PARENT1',
                        resourceType: 'application',
                        dependencies: ',CHILD1,CHILD2,',
                        changed     : false,
                ],
                'key2': [
                        folderName  : '/hello/world',
                        projectName : 'CHILD1',
                        resourceType: 'library',
                        dependencies: ',',
                        changed     : true,
                ],
                'key3': [
                        folderName  : '/hello/world',
                        projectName : 'CHILD2',
                        resourceType: 'library',
                        dependencies: ',CHILD1,',
                        changed     : false,
                ],
                'key4': [
                        folderName  : '/hello/world',
                        projectName : 'PARENT2',
                        resourceType: 'application',
                        dependencies: ',CHILD3,',
                        changed     : false,
                ],
                'key5': [
                        folderName  : '/hello/world',
                        projectName : 'CHILD3',
                        resourceType: 'library',
                        dependencies: ',',
                        changed     : false,
                ],
        ]
        simulatePodTemplate(psc, cloudName)
        when:
        bb.resolveDependencies()

        then:
        assert bb.resources[key].changed == expected

        where:
        key    || expected
        'key1' || true
        'key2' || true
        'key3' || true
        'key4' || false
        'key5' || false
    }

    // void updateParentsChangedStatus()
    def """When a child resource has changed, then all parent resources should be marked as changed"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        bb.resources = [
                'key1': [
                        projectName : 'PARENT1',
                        dependencies: ',CHILD1,',
                        changed     : false,
                ],
                'key2': [
                        projectName : 'PARENT2',
                        dependencies: ',CHILD2,',
                        changed     : false,
                ],
        ]
        simulatePodTemplate(psc, cloudName)
        when:
        bb.resources['key1'].changed = key1Changed
        bb.resources['key2'].changed = key2Changed
        bb.updateParentsChangedStatus(childProjectName, childChanged)

        then:
        assert bb.resources['key1'].changed == key1Expected
        assert bb.resources['key2'].changed == key2Expected

        where:
        childProjectName | childChanged | key1Changed | key2Changed | key1Expected | key2Expected
        'CHILD1'         | true         | false       | false       | true         | false
        'CHILD1'         | false        | false       | false       | false        | false
        'CHILD1'         | true         | true        | false       | true         | false
        'CHILD1'         | false        | true        | false       | true         | false
    }

    // int buidBarFiles()
    def """When buildBarFiles is called, then all resources that should be built are built"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        bb.resources = [
                'key1': [
                        folderName  : '/hello/world',
                        projectName : 'TEST_NAME_1',
                        resourceType: 'application',
                        changed     : true,
                        built       : false,
                ],
                'key2': [
                        folderName  : '/hello/world',
                        projectName : 'TEST_NAME_2',
                        resourceType: 'library',
                        changed     : false,
                        built       : false,
                ],
                'key3': [
                        folderName  : '/hello/world',
                        projectName : 'TEST_NAME_3',
                        resourceType: 'library',
                        changed     : false,
                        built       : true,
                ],
                'key4': [
                        folderName  : '/hello/world',
                        projectName : 'TEST_NAME_4',
                        resourceType: 'library',
                        changed     : true,
                        built       : true,
                ],
        ]
        simulatePodTemplate(psc, cloudName)
        when:
        getPipelineMock("container")(_) >> 'Command completed successfully'
        def numBuilt = bb.buildBarFiles()

        then:
        assert numBuilt == 1
    }

    // String getAllProjectFolders()
    def """When getAllProjectFolders is called, then all project folders are returned"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        bb.resources = [
                'key1': [folderName: 'folder1'],
                'key2': [folderName: 'folder2'],
                'key3': [folderName: 'folder3'],
        ]
        String expected = ' folder1 folder2 folder3'
        simulatePodTemplate(psc, cloudName)
        when:
        String result = bb.getAllProjectFolders()

        then:
        assert result == expected
    }

    // boolean createResource(String resourceName, String resourceType, String resourcePath, String resourceDependencies)
    def """When createResource is called with valid parameters, then CREATE_BAR_CMD is run with correct parameters"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [extraArgs: 'testing'],
                CONTAINER_NAME, BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        explicitlyMockPipelineStep("sh")
        explicitlyMockPipelineStep("echo")
        simulatePodTemplate(psc, cloudName)
        when:
        bb.createResource(resourceName, resourceType, resourcePath, dependencies)

        then:
        1 * getPipelineMock("echo")(
                'Building resource name ' + resourceName
                        + ', type ' + resourceType
                        + ', path ' + resourcePath
                        + ', barfile ' + resourceName + '-' + bb.extraArgs + '.bar'
        )
        1 * getPipelineMock("sh")(_) >> { _arguments ->
            assert _arguments[0].script.contains("${CREATE_BAR_CMD} -configuration . -data . ") == true
            if (dependencies != '') {
                assert _arguments[0].script.contains(' -p ' + dependencies + ' ') == true
            } else {
                assert _arguments[0].script.contains(' -p ') == false
            }
            assert _arguments[0].script.contains(expectedParams) == true
        }

        where:
        resourceName  | resourceType  | resourcePath   | dependencies || expectedParams
        'HELLO_WORLD' | 'application' | '/hello/world' | 'ALPHA BETA' || ' -a ' + resourcePath + ' -deployAsSource'
        'HELLO_WORLD' | 'application' | '/hello/world' | ''           || ' -a ' + resourcePath + ' -deployAsSource'
        'HELLO_WORLD' | 'library'     | '/hello/world' | 'ALPHA BETA' || ' -l ' + resourcePath + ' -deployAsSource'
        'HELLO_WORLD' | 'library'     | '/hello/world' | ''           || ' -l ' + resourcePath + ' -deployAsSource'
        'HELLO_WORLD' | 'msgset'      | '/hello/world' | 'ALPHA BETA' || ' -o ' + resourcePath + '/messageSet.mset'
        'HELLO_WORLD' | 'msgset'      | '/hello/world' | ''           || ' -o ' + resourcePath + '/messageSet.mset'
        'HELLO_WORLD' | 'msgflow'     | '/hello/world' | 'ALPHA BETA' || ' -o ' + resourcePath + '/' + resourceName + '.msgflow'
        'HELLO_WORLD' | 'msgflow'     | '/hello/world' | ''           || ' -o ' + resourcePath + '/' + resourceName + '.msgflow'
    }

    // void showBarFileContents(String barfile)
    def """When showBarFileContents is called, then bar file details are shown"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        def barFile = '/hello/world/foo.bar'
        explicitlyMockPipelineStep("sh")
        explicitlyMockPipelineStep("echo")
        simulatePodTemplate(psc, cloudName)
        when:
        bb.showBarFileContents(barFile)

        then:
        1 * getPipelineMock("echo")("***\n*** Contents of ${barFile}\n***")
        1 * getPipelineMock("sh")(_) >> { _arguments ->
            assert _arguments[0].contains(SHOW_BAR_CMD) == true
            assert _arguments[0].contains("${barFile}") == true
        }
    }

    // boolean isValidBarFile(String barfile)
    def """When isValidBarFile is called with valid parameters and data, then it should return true"""() {
        setup:
        BarBuilder bb = new BarBuilder(psc, script, [:], CONTAINER_NAME,
                BAR_FOLDER, CREATE_BAR_CMD, SHOW_BAR_CMD, CMD_RESPONSES_URL)
        simulatePodTemplate(psc, cloudName)
        String bOut = barOutput
        String bFile = barFile
        String errMsg = echoMsg
        when:

        getPipelineMock("sh")(_) >> bOut
        def actualResult = bb.isValidBarFile(bFile)

        then:
        (expectedResult ? 0 : 1) * getPipelineMock("echo")({
            it == errMsg
        })
        assert actualResult == expectedResult

        where:
        barFile        | barOutput                                                                  | echoMsg                                                             | expectedResult
        null           | null                                                                       | 'ERROR: barfile is null!'                                           | false
        '/tmp/foo.bar' | null                                                                       | 'ERROR: Show bar file for ' + barFile + ' failed!'                  | false
        '/tmp/foo.bar' | 'a\n' + barFile + ':\n\nc'                                                 | 'ERROR: Show bar file for ' + barFile + ' failed!'                  | false
        '/tmp/foo.bar' | 'a\nb\nc\nBIP8071I: Successful command completion.'                        | 'ERROR: Could not find start of output from show bar file command!' | false
        '/tmp/foo.bar' | 'a\n' + barFile + ':\nBIP8071I: Successful command completion.'            | 'ERROR: File ' + barFile + ' is empty or invalid!'                  | false
        '/tmp/foo.bar' | 'a\n' + barFile + ':\na\nb\nc\n\nBIP8071I: Successful command completion.' | null                                                                | true
    }
}