package com.evernorth.cloudnativebuild.model

import com.cloudbees.groovy.cps.NonCPS
import org.apache.commons.lang3.builder.HashCodeBuilder

/**
 * Credential wrapper for CNP module bindings
 */
class Credential implements Serializable {
    /**
     * type - The type of credential. At this time only username-password, string and Azure credential is supported
     */
    String type = 'usernamePassword'

    /**
     * id - The credential id defined in jenkins
     */
    String id = ''

    /**
     * prefix - When multiple credentials of same type is required by a module, this is used to define the env var
     * possible values are
     *  GIT
     *  CX
     *
     * if it is not set, then the module uses only 1 credential
     */
    String prefix = ''

    /**
     * env - this is used to define the environment, this credential should be used
     * possible values are
     *  DEV
     *  QA
     *  UAT
     *  PROD
     *  DR
     *
     * if it is not set, then it is common to all environments
     */
    String env = ''

    /**
     * scope - this is used to define the scope of this credential when same module is used for multiple destinations
     * this is custom defined and should match with the options passed to calls along with arguments
     * sample values are
     * AWS
     * AZ (azure)
     * OC (openshift)
     *
     * if it is not set, then it is common to all environments
     */
    String scope = ''

    /**
     * This is used to supply custom variable name for secret text type cred,
     * intended to propagate to callback jobs
     */
    String variable = null

    /**
     * These are used to supply custom variable name for secret text type cred,
     * intended to propagate to callback jobs
     */
    String usernameVariable = null
    String passwordVariable = null

    @NonCPS
    static List<Credential> convertToArrayListOfCredentials(List<Map<String, String>> credsMap) {
        List<Credential> listOfCreds = []
        credsMap?.each {
            if (it instanceof Map) {
                Credential temp = new Credential(it)
                listOfCreds.add(temp)
            }
        }
        listOfCreds
    }

    @NonCPS
    boolean isReplaceable(Credential incomingCred) {
        this == incomingCred
    }

    @Override
    @NonCPS
    boolean equals(Object obj) {
        Credential credential = obj as Credential
        this.prefix == credential.prefix &&
            this.type == credential.type &&
            this.env == credential.env &&
            this.scope == credential.scope
    }

    @Override
    int hashCode() {
        new HashCodeBuilder(17, 37).
            append(prefix).
            append(type).
            append(env).
            append(scope).
            toHashCode()
    }

    @Override
    @NonCPS
    String toString() {
        StringBuilder builder = new StringBuilder()
        builder.append('{')
        builder.append '"type":"' + type + '"'
        builder.append(', "id":"' + id + '"')
        if (prefix) {
            builder.append(', "prefix":"' + prefix + '"')
        }
        if (env) {
            builder.append(', "env":"' + env + '"')
        }
        if (scope) {
            builder.append(', "scope":"' + scope + '"')
        }
        builder.append('}')
        builder.toString()
    }
}
