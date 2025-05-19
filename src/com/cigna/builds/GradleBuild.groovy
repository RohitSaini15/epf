package com.cigna.builds

import com.cigna.base.Phase
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * For builds utilizing Gradle
 */
class GradleBuild extends Build {
    //extraArgs will be used on all calls to gradle. This is to be used for additional arguments for the commandline.
    //To override the tasks to execute, use the buildTasks property or publishTasks property. 
    private String extraArgs = ''
    //default gradle command to gradle without path info. Gradle must be installed in the OS path. To use a gradle 
    //wrapper set the gradleWrapperScript to the path and file name of the script (usually ./gradlew).
    private String gradleCommand = 'gradle'
    private List validPublishOptions = ['all', 'none', 'snapshots', 'releases']
    GradleBuild() {
        containerName = 'gradlevlatest'
        containerImage = 'enterprise-devops/gradle'
        containerVersion = 'latest'
        containerMemory = '2000Mi'
        containerCpu = '1000m'
        stashIncludePattern = '**/build/**'
    }

    @Override
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        List issues = []
        if (config.publish && !(config.publish in validPublishOptions)) {
            issues += ["Gradle 'publish' setting has invalid value: '${config.publish}'. Valid values are $validPublishOptions."]
        }
        if (!config.publish) {
            config.publish = 'releases'
        }
        return super.validate(requiresBranchPattern, phase) + issues
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            1000,
            500,
            1000,
            env
        )
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

        additionalPodConfig = [
                volumes   : [],
                containers: [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }
    
    static String setupGradleCommand(Object script, String wrapper) {
        if (wrapper) {
            //if using gradle wrapper, the gradlew script needs to have execute permission
            try {
                script.sh("chmod +x ${wrapper}")
            } catch(Exception e) {
                script.echo("Exception adding execute permission to gradle wrapper. If it is not already set for execute permissions by git, builds may fail: ${e.getLocalizedMessage()}")
            }
            return wrapper
        } else {
            return 'gradle'
        }
    }

    /**
     * There are like 5 places that might have a gradle user home setting.
     * This method gives a clear and uniform precedence to them, and also updates env vars to be blank
     * if their value is the string 'null'.
     */
    static String findGradleUserHome(Object script, Map gradleConfig) {
        if (script.env?.GRADLE_USER_HOME == 'null') {
            script.env.GRADLE_USER_HOME = ''
        }
        if (script.env?.gradleUserHome == 'null') {
            script.env.gradleUserHome = ''
        }
        return gradleConfig?.gradleUserHome ?: gradleConfig?.gradleuserHome ?: script.env?.gradleUserHome ?: script.env?.GRADLE_USER_HOME ?: ''
    }

    @Override
    void executePreBuildStage() {
        commonPreBuildSteps()
        
        String finalGradleHome = findGradleUserHome(script, config.gradle)
        
        // add the user home to extraArgs if configured
        extraArgs = Utils.buildCommandArgs(config.gradle?.extraArgs, ['-g', finalGradleHome])
        if (FeatureFlags.debug) {
            script.echo("extraArgs = $extraArgs")
        }
        //if option is set to use a gradle wrapper script then change the gradle command
        gradleCommand = setupGradleCommand(script, config.gradle?.gradleWrapperScript)
        
        if (FeatureFlags.debug) {
            script.echo("Gradle command to run: ${gradleCommand}")
        }
    }

    @Override
    void executeBuildAndTestStage() {
        //Parses the buildTasks as either a collection of strings or a comma separated string to get a list of tasks to
        //execute in gradle. Defaults to clean and build.
        //Suggest that build is used alone to enable caching for non-release builds that publish artifacts and test is
        //used alone in cases where no artifacts are being deployed outside of the container.
        def buildTasks = parseTaskList(config.gradle?.get('buildTasks')) ?: 'clean build'
        if (FeatureFlags.debug) {
            script.echo("Build tasks to run: ${buildTasks}")
        }
        script.sh(Utils.buildCommandArgs(gradleCommand, extraArgs, buildTasks))
    }
    /**
     * Overrides the superclass method that determines if publishing should happen. If publishing is
     * being skipped, logs the reason for skipping. But still 
     * avoids calling gradle properties if not necessary.
     */
    @Override
    protected boolean shouldRunPublish() {
        // do we have a reason to skip publish?
        String notPublishingReason = ''
        if (config.get('publishEnabled') == false) {
            notPublishingReason = 'publishEnabled is false'
        } else if (!isPublishBuild()) {
            notPublishingReason = "Project version/branch name does not match 'publish' property '${config.publish}'" 
        }
        // if there's a reason, log it
        if (notPublishingReason) {
            script.echo("$notPublishingReason. Skipping publish steps.")
        }
        
        // we return a boolean here - if there's no reason to skip publish, then the reason string
        // is empty, so it's falsey and we return the opposite of that, which is true (we should publish).
        // If there is a reason to skip publishing, then the string is truthy, so return false (don't publish).
        return !notPublishingReason
    }

    @Override
    void executePublishStage() {
        def publishTasks = parseTaskList(config.gradle?.get('publishTasks')) ?: 'publish'
        if (FeatureFlags.debug) {
            script.echo("Publish tasks to run: ${publishTasks}")
        }
        def repositoryName = config.gradle?.publishRepositoryName ?: 'maven'
        //get maven credentials
        if (config.artifactory?.credentialsId) {
            script.withCredentials([
                    script.usernamePassword(
                            credentialsId: config.artifactory.credentialsId,
                            usernameVariable: 'ARTIFACTORY_USER',
                            passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
            ]) {
                String credentials = '-P' + repositoryName + 'Username=$ARTIFACTORY_USER -P' + repositoryName + 'Password=$ARTIFACTORY_APIKEY'
                script.sh(Utils.buildCommandArgs(gradleCommand, extraArgs, publishTasks, credentials))
            }
        } else {
            //if not using the Jenkinsfile to pass credentials, run the gradle command without embedded credentials. 
            //this assumes the project has used another method to provide credentials through Jenkins or gradle configs
            
            script.echo('Artifactory credentialId not set. Running publish without any pipeline cred setup.')
            script.sh(Utils.buildCommandArgs(gradleCommand, extraArgs, publishTasks))
        }
    }

    /**
     * Checks the publish option and determines if this run should publish to maven. If set to all or if set to
     * snapshots and this is a snapshot version, or if set to releases and this is a release branch and not a snapshot
     * version it will return true. If publish is not set or invalid it will default to 'releases' functionality and 
     * it will publish if it's a release branch and not a snapshot.
     * @return true if the build should publish to maven, otherwise false
     */
    boolean isPublishBuild() {
        switch (config.publish){
            case 'all':
                return true
            case 'none':
                return false                     
            case 'snapshots':
                return isSnapshotVersion()
            case 'releases':
                return currentBranchIsReleaseBranch() && !isSnapshotVersion()
            default:
                return currentBranchIsReleaseBranch() && !isSnapshotVersion()
        }
    }

    /**
     * Determines if the gradle project version is a snapshot by looking at the version string. Current logic simply 
     * looks for the word "SNAPSHOT" to appear in the version string.
     * @return true if this is a snapshot build, otherwise false
     */
    boolean isSnapshotVersion(String buildVersion = getVersionFromGradle()) {
        if (!buildVersion) {
            script.echo("Unable to determine project version - assuming a release version.")
        }
        return buildVersion?.contains("SNAPSHOT")
    }
    
    /**
     * Calls the gradle properties task to get the version property and parse it from stdout
     * @return the version of the application being built, or null if a version can't be determined
     */
    String getVersionFromGradle() {
        def versionPropertyTaskCommand = 'properties'
        try {
            String propertyQuery = script.sh(script: Utils.buildCommandArgs(gradleCommand, extraArgs, versionPropertyTaskCommand), returnStdout: true)
            def version = propertyQuery =~ /(?m)^version: (.*)/
            version?.find()
            String buildVersion = version?.group(1)?.trim()
            if (FeatureFlags.debug) {
                script.echo("Build Version: ${buildVersion}")
            }
            return buildVersion
        } catch (Exception e) {
            script.echo("Error trying to get project version: ${e.getLocalizedMessage()}")
            return null
        }
    }

    /**
     * Parses an Iterable of strings into a space separated collection or returns the string directly if it's a string
     * This is used to convert a list object list of gradle tasks into a form for the command line.
     * @param taskList List of tasks to run as either a collection of strings or just returns the string if given one
     * @return the space separated list of tasks for the command line.
     */
    static String parseTaskList(taskList){
        if (taskList instanceof Iterable) {
            return taskList?.join(' ')
        } else {
            return taskList
        }
    }
}
