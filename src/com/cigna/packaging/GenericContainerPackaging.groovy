package com.cigna.packaging

import com.cigna.base.Phase
import com.cigna.common.exception.UnassignableTypeException
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS

/**
 * Defines container packaging. Assumes that a build of some sort has already been completed. We'll have stash stuff at
 * the end of the build and unstash it here.
 */
abstract class GenericContainerPackaging extends Packaging {
    protected static final String DEFAULT_DOCKER_REGISTRY = 'quay.sys.cigna.com'
    protected static final String QUAY_CONTAINER = 'quay-clivv191'

    GenericContainerPackaging() {
        baseValidationItems = [
            'image.name'
        ]
    }
    protected String imageName
    protected String imageTag
    protected String dockerRegistry = DEFAULT_DOCKER_REGISTRY
    protected String custommsg = config?.customMessage ?: ''
    protected List imageTagMapList
    protected List imageUrls

    @Override
    void packageApplication() {
        determineImageVars()
        psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
            containerBuildAndPushImage()
        }
        if (config?.quay) {
            psc.podSelector.select(psc, QUAY_CONTAINER, Utils.cloud(config)) {
                quayScanImage()
                quayExpireImage()
                quaySetVisibility()
                if (!config.containsKey('disableDescription') || !config.disableDescription) {
                    quaySetDescription()
                }
            }
        }
    }

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        if (config?.image?.buildArgs) {
            if (!(Map.isAssignableFrom(config?.image?.buildArgs?.getClass()))) {
                throw new UnassignableTypeException('Image build args must be submitted in Map form')
            }
        }

        if (config?.quay) {
            additionalValidationItems += [
                'image.org',
                ['quay.credentialsId', 'quay.apiTokenCredentialsId'],
            ]
        } else if (config?.ecr) {
            additionalValidationItems += [
                'ecr.credentialsId',
                'ecr.rolename',
            ]
        } else {
            additionalValidationItems += [
                ['quay', 'ecr']
            ]
        }

        List versionIssues = super.validate(requiresBranchPattern, phase)
        versionIssues
    }

    protected void determineImageVars() {
        dockerRegistry = config?.dockerRegistry ?: DEFAULT_DOCKER_REGISTRY
        script.echo("Setting DockerRegistry to be ${dockerRegistry} ")

        def suppressCommitTag = config?.containsKey('suppressCommitTag') ? config.suppressCommitTag : false

        imageName = (config?.quay) ? "${config.image.org}/${config.image.name}" : "${config.image.name}"
        if (!suppressCommitTag) {
            imageTag = "${script.env.GIT_COMMIT[0..7]}"
        }
        imageTagMapList = suppressCommitTag ? [] : [[tag: "${imageTag}", expire: true]]
        imageUrls = suppressCommitTag ? [] : ["${dockerRegistry}/${imageName}:${imageTag}"]
        for (tag in config?.image?.tags) {
            script.echo("Building tag: ${tag?.tag} Expire: ${tag?.expire}")
            imageTagMapList.add(tag)
            imageUrls.add("${dockerRegistry}/${imageName}:${tag?.tag}")
        }
    }

    protected String constructBuildArgsString() {
        String argString = ''
        config.image?.buildArgs?.each { key, value ->
            argString = argString + "--build-arg ${key}=\"${value}\" "
        }
        argString
    }

    protected void quayScanImage() {
        boolean runScan = (config?.image?.runScan == null) ? false : config?.image?.runScan

        if (runScan) {
            script.echo('!!!!! ----- Quay Scanner no longer generates accurate vulnerability reports, and is not supported anymore. Please refer to https://confluence.sys.cigna.com/x/ahyMTw to scan your images with ACS  ----- !!!!!')
        }
    }

    protected void quayExpireImage() {
        script.withCredentials([script.string(
            credentialsId: "${config.quay.credentialsId ?: config.quay.apiTokenCredentialsId}",
            variable: 'quayToken'
        )]) {
            imageTagMapList.each { tag ->
                String override = tag?.override
                if (override) {
                    String parseOverride = override - '[' - ']'
                    script.echo("Override previous set tag to ${parseOverride}")
                    script.sh(
                        "quay repository expire \"${imageName}\" --host \"https://${dockerRegistry}\" \
                            ${dockerRegistry}/${tag?.tag} --expiration ${parseOverride} \
                            --token \"${script.quayToken}\"".replaceAll(/\s+/, ' '))
                } else {
                    String defaultExpiration = config.quay?.defaultExpiration ?: 'never'
                    if (!tag.containsKey('expiration')) {
                        tag.expiration = tag.expire ? '2w' : defaultExpiration
                    }
                    if (tag.expiration == 'never') {
                        String warning = 'WARNING: image will not expire;set either tag.expiration or quay.defaultExpiration'
                        script.echo(warning)
                    } else {
                        script.echo("Setting image to expire in ${tag.expiration}")
                    }
                    script.sh(
                        "quay repository expire \"${dockerRegistry}/${imageName}:${tag?.tag}\" --host \"https://${dockerRegistry}\" \
                        --expiration ${tag.expiration} --token \"${script.quayToken}\"".replaceAll(/\s+/, ' '))
                }
            }
        }
    }

    protected void quaySetVisibility() {
        script.echo('Setting image visibility to public')
        script.withCredentials([script.string(
            credentialsId: "${config.quay.credentialsId ?: config.quay.apiTokenCredentialsId}",
            variable: 'quayToken'
        )]) {
            script.sh("quay repository visibility \"${imageName}\" \
            --host \"https://${dockerRegistry}\" public --token \"${script.quayToken}\" ".replaceAll(/\s+/, ' '))
        }
    }

    protected void quaySetDescription() {
        String repoDescription = config?.quay?.description ?:
            "Managed by the Enterprise Pipeline Framework (EPF).\nBuilt with this [Jenkins job](${script.env.JOB_URL}).\n" +
                "Maintained from this [GitHub repo](${gitUrl()}).\n"

        script.echo('Setting repo description')
        script.withCredentials([script.string(
            credentialsId: "${config.quay.credentialsId ?: config.quay.apiTokenCredentialsId}",
            variable: 'quayToken'
        )]) {
            script.sh("""quay repository description \"${dockerRegistry}/${imageName}\" \"${repoDescription}\" \
            --host \"https://${dockerRegistry}\" --token \"${script.quayToken}\" """.replaceAll(/\s+/, ' '))
        }
    }

    abstract protected void containerBuildAndPushImage()
}
