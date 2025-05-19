// notes on why the phases are how they are: https://confluence.sys.cigna.com/display/DvOp/EPF+Jenkinsfile+Design

def epf_branch = env.CHANGE_BRANCH ?: env.BRANCH_NAME
library "epf@$epf_branch"

// stuff for calling the test-wright job
def urlEncode = { String s -> URLEncoder.encode(s, 'UTF-8') }
String testCategories = "test-categories=${urlEncode('epf/ci')}"
String jobOverrides = "job-detail-overrides=${urlEncode(""":library/name "epf" :library/branch "${epf_branch}" :job/build-params {"TEST_LIB_NAME" "epf" "TEST_LIB_VER" "${epf_branch}"}""")}"

def releaseBranchPattern = '^(release.*|hotfix.*|main)$'
boolean isProductionDeployment = epf_branch ==~ /$releaseBranchPattern/
String testWrightEnvironment = isProductionDeployment ? 'prod' : 'nonprod'

// Necessary to be able to trigger both regular and HA builds without need to hand edit via replay
def cloudNameDef = env.CLOUD_NAME ?: 'epf-openshift-devops1'
def testHostURL = env.TEST_HOST_URL ?: 'https://orchestrator18.orchestrator-v2.sys.cigna.com/'
def testJobName = env.TEST_JOB_NAME ?: 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev'

env.CNP_LOG_LEVEL = 'TRACE'
env.CNP_OVERRIDE_COMMON = 'true'
env.JOB_BUILD_CRED = 'JENKINS_AUTH_CREDS'
env.CX_CREDENTIAL = 'checkmarx-devops-creds'
env.SONAR_CREDENTIAL_ID = 'sonarqube-token'

def REQUESTED_BY = 'ei0733'
def ASSIGNED_TO = REQUESTED_BY

env.CNP_DEFAULT_JAVA_IMAGE = 'cnp/cnp-docker-maven-java11:1.2.8'
def JAVA_IMAGE = env.CNP_DEFAULT_JAVA_IMAGE
def jobCreds = env.JOB_BUILD_CRED

cignaBuildFlow {
    githubConnectionName = 'github'
    commitStatusName = 'Enterprise Pipeline Framework'
    cloudName = cloudNameDef
    logHistoryCount = '10'
    webexTeamsRoom = 'bd7cdcb0-27a4-11ed-af92-f5afe966fcf4'
    resourceScaleFactor = 1
    featureFlags = [verbose: true]
    phases = [
        [
            deploymentType        : 'phases',
            branchPattern         : '.*',
            sdlcEnvironment       : 'nonprod',
            isProductionDeployment: false,
            // 'non-prod deploy' is building
            phases                : [
                [
                    moduleType            : 'maven',
                    moduleName            : 'cnp-build-publish-maven-create',
                    stageName             : 'Compile code',
                    branchPattern         : '.*',
                    sdlcEnvironment       : '',
                    isProductionDeployment: false,
                    container             : [
                        memory: 8000,
                        cpu   : 2000,
                    ],
                    args                  : [
                        publish    : false,
                        credentials: [],
                        buildGoals : '-q -B compile -DskipTests'
                    ],
                ]
            ],
            testing               : [
                // adjudicator wants non-prod testing, but we're going that in a parallel block below,
                // so this is a placeholder
                [
                    testType                    : 'QEAMaven',
                    branchPattern               : '.*',
                    frameworkType               : 'maven',
                    build                       : 'Java',
                    stage                       : 'integration tests (placeholder)',
                    useOrchestratorMavenSettings: true,
                    maven                       : [
                        m2RepoPath: '/tmp/.m2/repository'
                    ],
                    container                   : [
                        image : JAVA_IMAGE,
                        memory: 8000,
                        cpu   : 3000,
                    ],
                    commandString               : 'echo "tests in parallel block to run next" #'
                ]
            ]
        ],
        // check that the tag that would be created doesn't already exist if we're building a PR 
        [
            freestyleType   : 'Precheck POM version',
            branchPattern   : '^PR-\\d+$',
            sdlcEnvironment : 'dev',
            container       : [
                image: JAVA_IMAGE
            ],
            extraCredentials: [
                usernamePassword(
                    credentialsId: 'svpconduitonboard-gh-token',
                    passwordVariable: 'GIT_TOKEN',
                    usernameVariable: 'GIT_USER'
                )
            ],
            withEnv         : [
                "ARTIFACTORY_ROOT_URL=https://cigna.jfrog.io/artifactory"
            ],
            script          : "./Tag-and-Release-CI.sh precheck"
        ],
        [
            parallelType   : 'jenkins',
            stageName      : 'Tests',
            branchPattern  : '.*',
            sdlcEnvironment: 'dev',
            phases         : [
                'test wright': [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '^(main|PR-\\d+)$',
                    sdlcEnvironment       : testWrightEnvironment,
                    isProductionDeployment: isProductionDeployment,
                    stageName             : 'Test-Wright test suite',
                    args                  : [
                        jobName    : testJobName,
                        jenkinsURL : testHostURL,
                        buildParams: "$testCategories&$jobOverrides",
                        credentials: [[id: jobCreds]]
                    ],
                    resourceScaleFactor   : 1
                ],
                'unit tests' : [
                    testType                    : 'QEAMaven',
                    branchPattern               : '.*',
                    frameworkType               : 'maven',
                    build                       : 'Java',
                    stageName                   : 'unit/integration tests',
                    useOrchestratorMavenSettings: true,
                    maven                       : [
                        m2RepoPath: '/tmp/.m2/repository'
                    ],
                    container                   : [
                        image : JAVA_IMAGE,
                        memory: 8000,
                        cpu   : 3000,
                    ],
                    resourceScaleFactor         : 1,
                    commandString               : 'mvn -B verify',
                    email                       : [
                        branchPattern: 'main',
                        recipients   : 'EDOPipelines&Compliance@Cigna.com',
                        body         : 'Please find attached EPF JaCoCo report',
                        zip          : [
                            folder: 'target/site/jacoco',
                            name  : 'jacoco.zip'
                        ]
                    ]

                ]
            ]
        ],
        [
            parallelType   : 'jenkins',
            stageName      : 'Quality Scans',
            branchPattern  : '.*',
            sdlcEnvironment: 'dev',
            phases         : [
                checkmarx: [
                    moduleType            : 'quality',
                    moduleName            : 'cnp-quality-profile-checkmarx',
                    stageName             : 'Checkmarx',
                    subCommand            : 'scan',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    args                  : [
                        projectName               : "enterprise-pipeline-framework",
                        cxProductionBranch        : "main",
                        teamName                  : 'devops',
                        cxVulnerabilityThreshValue: 'RED',
                        cxExcludeFolders          : 'test,target'
                    ],
                    resourceScaleFactor   : 1
                ],
                // application type is left off to prevent an additional maven build
                sonar    : [
                    moduleType            : 'quality',
                    moduleName            : 'cnp-quality-check-sonarqube',
                    stageName             : 'SonarQube',
                    subCommand            : 'scan',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    args                  : [
                        projectName: 'enterprise-pipeline-framework'
                    ],
                    resourceScaleFactor   : 1
                ],
            ]
        ],
        [
            checkpointType: 'simple',
            branchPattern : 'main',
            name          : 'After Build & Test'
        ],
        [
            freestyleType         : 'Tag Release',
            branchPattern         : 'main',
            sdlcEnvironment       : 'prod',
            isProductionDeployment: true,
            container             : [
                image: JAVA_IMAGE
            ],
            extraCredentials      : [
                usernamePassword(
                    credentialsId: 'svpconduitonboard-gh-token',
                    passwordVariable: 'GIT_TOKEN',
                    usernameVariable: 'GIT_USER'
                )
            ],
            withEnv               : [
                "ARTIFACTORY_ROOT_URL=https://cigna.jfrog.io/artifactory"
            ],
            script                : "./Tag-and-Release-CI.sh release"
        ],
        [
            checkpointType: 'simple',
            branchPattern : 'main',
            name          : 'After Release Tagged'
        ],
        [
            deploymentType        : 'phases',
            branchPattern         : 'main',
            sdlcEnvironment       : 'prod',
            isProductionDeployment: true,
            ticket                : [
                ticketType                : 'Servicenow',
                branchPattern             : releaseBranchPattern,
                isProductionDeployment    : true,
                changeEnvironment         : 'prod',
                sdlcEnvironment           : 'prod',
                credentialsId             : 'SERVICE_NOW_TOKEN',
                title                     : 'SCT0000142',
                cmdb_ci                   : 'Enterprise Pipeline Framework',
                requested_by              : REQUESTED_BY,
                assigned_to               : ASSIGNED_TO,
                plannedDuration           : 60,
                u_emergency_contact_person: ASSIGNED_TO,
                u_emergency_contact_number: 1234567890
            ],
            phases                : [
                // tagging is done earlier (for now), so this is a placeholder
                [
                    freestyleType         : 'Tag Release (placeholder)',
                    branchPattern         : 'main',
                    sdlcEnvironment       : 'prod',
                    isProductionDeployment: true,
                    container             : [
                        image: JAVA_IMAGE
                    ],
                    script                : "echo 'release placeholder'"
                ]
            ],
            testing               : [
                // there's no 'deploy' per se`, so prod testing is the testing that happened earlier
                [
                    testType     : 'QEAMaven',
                    branchPattern: 'main',
                    frameworkType: 'maven',
                    build        : 'Java',
                    stage        : 'integration tests (placeholder)',
                    commandString: 'echo "tested via non-prod testing" #',
                ]
            ]
        ]
    ]
}
