package com.cigna.common.utils

import com.cloudbees.groovy.cps.NonCPS

/**
 * Common AWS utilities used across multiple phases
 */
class AWSUtils {

    /*
     * Derive the role name from the AWS configuration object
     *
     * @param awsConfig The aws map in the configuration
     * @return The role ARN
     *         e.g. arn:aws:iam::1234:role/Enterprise/SOMEROLE
     */

    static String getTargetAccountRoleARN(Map<String, String> awsConfig) {
        String targetAccountRoleARN = ''

        if (awsConfig?.targetAccountRoleARN) {
            targetAccountRoleARN = awsConfig.targetAccountRoleARN
        } else {
            String rolePath = awsConfig?.rolePath ? "${awsConfig.rolePath}/" : ''
            String accountRoleName = awsConfig?.accountRoleName ?: ''
            String targetAccount = awsConfig?.targetAccount ?: ''
            targetAccountRoleARN = "arn:aws:iam::${targetAccount}:role/${rolePath}${accountRoleName}"
        }

        targetAccountRoleARN
    }

    @NonCPS
    static def awsMounts() {
        [
                [
                        name     : 'aws-creds',
                        mountPath: '/home/jenkins/.aws',
                ],
                [
                        name     : 'ecr-creds',
                        mountPath: '/home/jenkins/.ecr',
                ],
        ]

    }

    @NonCPS
    static def awsVolumes() {
        [
                [
                        name    : 'aws-creds',
                        emptyDir: [
                                medium: ''
                        ]
                ],
                [
                        name    : 'ecr-creds',
                        emptyDir: [
                                medium: ''
                        ]
                ]
        ]
    }

    @NonCPS
    static def awsEnvironment() {
        [
                [
                        name : 'AWS_SHARED_CREDENTIALS_FILE',
                        value: '/home/jenkins/.aws/credentials',
                ],
                [
                        name : 'AWS_PROFILE',
                        value: 'saml',
                ],
                [
                        name : 'SAML2AWS_PROFILE',
                        value: 'default',
                ],
        ]
    }
}
