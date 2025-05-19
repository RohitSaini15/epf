import com.cigna.common.utils.PlzUtils
import spock.lang.Specification

class PlzUtilsSpec extends Specification {

    def """Construct multi module build command properly creates full build command"""() {
        expect:
            PlzUtils.constructMultiModuleBuildCommand(config, labels) == command
        where:
            config << [
                [verbosityFlag: '-vvv', extraBuildArgs: 'dev', modules: ['//module/aws/module1', '//module/aws/module2']],
                [modules: ['//module/aws/module1']],
                [extraBuildArgs: 'prod', modules: ['//module/gcp/module1', '//module/gcp/module2']],
            ]

            labels << [
                null,
                ['label1', 'label2'],
                ['label1', 'label2']
            ]

            command << [
                'plz build //module/aws/module1/... --show_all_output dev -vvv && ' +
                    'plz build //module/aws/module2/... --show_all_output dev -vvv',
                'plz build //module/aws/module1/... -i label1 -i label2 --show_all_output',
                'plz build //module/gcp/module1/... -i label1 -i label2 --show_all_output prod && ' +
                    'plz build //module/gcp/module2/... -i label1 -i label2 --show_all_output prod'
            ]
    }

    def """Construct multi module test command properly creates full test command"""() {
        expect:
            PlzUtils.constructMultiModuleTestCommand(config, labels) == command
        where:
            config << [
                [verbosityFlag: '-vvv', extraTestArgs: 'dev', modules: ['//module/aws/module1', '//module/aws/module2']],
                [modules: ['//module/gcp/module1']],
            ]

            labels << [
                ['lint'],
                null
            ]

            command << [
                'plz test //module/aws/module1/... -i lint --show_all_output dev -vvv && ' + 
                    'plz test //module/aws/module2/... -i lint --show_all_output dev -vvv',
                'plz test //module/gcp/module1/... --show_all_output'
            ]
    }

    def """Construct multi module deploy command properly creates full deployment command"""() {
        expect:
            PlzUtils.constructMultiModuleDeployCommand(config) == command
        where:
            config << [
                [verbosityFlag: '-vvv', extraArgs: 'dev', modules: ['//module/aws/module1', '//module/aws/module2']],
                [modules: ['//module/azure/module1'], moduleDeployTarget: 'deluxeDeploy'],
            ]

            command << [
                'plz run //module/aws/module1:deploy --show_all_output dev -vvv' +
                    ' && plz run //module/aws/module2:deploy --show_all_output dev -vvv',
                'plz run //module/azure/module1:deluxeDeploy --show_all_output',
            ]
    }

    def """Construct args properly appends arguments on the end of a command"""() {
        expect:
            PlzUtils.constructArgs(extraArgs, verbosityFlag) == args

        where:
            extraArgs << ['dev', 'test', '', '']
            verbosityFlag << ['-vvv', '', '-vvv', '']
            args << [
                ' --show_all_output dev -vvv',
                ' --show_all_output test',
                ' --show_all_output -vvv',
                ' --show_all_output',
            ]
    }
}