import com.cigna.common.utils.AWSUtils
import spock.lang.Specification

class AWSUtilsSpec extends Specification {

    def """Construct target account ARN from aws config"""() {
        
        expect:
            AWSUtils.getTargetAccountRoleARN(awsConfig) == targetAccountRoleARN
        where:
            awsConfig << [
                [
                    targetAccount: '1234',
                    accountRoleName: 'DEPLOYROLE',
                ],
                [
                    targetAccount: '1234',
                    accountRoleName: 'DEPLOYROLE',
                    rolePath: 'Enterprise',
                ],
                [
                    targetAccountRoleARN: 'arn:aws:iam::1234:role/tf/DEPLOYROLE',
                ]
            ]

            targetAccountRoleARN << [
                'arn:aws:iam::1234:role/DEPLOYROLE', 
                'arn:aws:iam::1234:role/Enterprise/DEPLOYROLE', 
                'arn:aws:iam::1234:role/tf/DEPLOYROLE'
            ]
    }
}