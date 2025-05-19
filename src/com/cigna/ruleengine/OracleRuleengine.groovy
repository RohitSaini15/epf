package com.cigna.ruleengine

import com.cigna.common.utils.FeatureFlags
import com.cigna.common.kubernetes.PodTemplateCreator
import hudson.Functions

/**
 *  class that defines the ruleengine
 */
class OracleRuleengine extends Ruleengine {
    OracleRuleengine() {
        containerName = 'ruleenginev10'
        containerImage = 'enterprise-devops/ruleengine'
        containerVersion = '1.0'
        containerCpu = '2000m'
        containerMemory = '2000Mi'
        baseValidationItems = [
            'ruleengineType',
            'oracle.pathForBuildXml',
        ]
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            2000,
            2000,
            2000,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
            ]
        super.prePodConfig()
    }

    @Override
    void runApplication() {
        try {
            String file = config.oracle?.pathForBuildXml ?: "${WORKSPACE}/src/build.xml"
            String workDir = '/app/ora_rules_engine'
            String basePath = ''
            script.echo("Build path = ${file}")
            script.container(containerName) {
                if (file.contains('build.xml')) {
                    script.echo('pathForBuildXml is correct')
                } else {
                    script.error('pathForBuildXml is not correct')
                }
                script.echo('################ Oracle Ruleengine Started ############################')
                basePath = script.sh(
                    script: "set +x | sed s/build.xml// <<<${file} | tr -d \"\\n\\t\\r\"",
                    returnStdout: true
                )
                script.echo("${basePath}")
                script.echo('################ Install Process Started ##########################')
                script.sh("${workDir}/ORE.sh -install")
                script.echo('################ Install Process End ##############################')
                script.echo('################ File Manipulation Process Started ################')
                script.sh(
                    script: """ set +x
                    basePath=\$(sed "s/build.xml//" <<<${file})
                    cp ${file} \${basePath}result.xml
                    num=\$(grep -n "\\<profile.dbupdate\\>" \${basePath}result.xml | cut -d : -f 1 )
                    num="\$((num - 1))"
                    sed -i '1',"\${num}d" \${basePath}result.xml
                    #cat \${basePath}result.xml
                    numend=\$(grep -n "\\<profile.dbupdatefinal\\>" \${basePath}result.xml | cut -d : -f 1 )
                    sed -i "\${numend}",'\$d' \${basePath}result.xml
                    echo "BasePath : \${basePath}"
                    echo "Line Number : \${num}"
                    echo "Line Number : \${numend}"
                    cat ${basePath}result.xml | tr -d " \t\r"
                """
                )
                script.echo('################ File Manipulation Process Completed ##############')
                script.sh(
                    script: """ set +x
                if grep -q "\\<resourcepath\\>" ${basePath}result.xml
                then
                    while read line; do
                        if !(grep -q '<!--' <<<\$line)
                        then
                            if (grep -q "\\<resourcepath\\>" <<<\$line)
                            then
                                IFS=\$'\n' lines+=\$line
                            fi
                        fi
                    done < ${basePath}result.xml
                    #declare -p lines
                    for i in \${lines[@]}
                    do
                        #echo \$i
                        subString=`echo "\$i" | awk -F'"' '{print \$2 }'`
                        #echo \$subString
                        finalPath="src/\$subString"
                        echo "================================================================"
                        echo "File Path For Master.xml : \${WORKSPACE}/\$finalPath/master.xml"
                        echo "================================================================"
                        ${workDir}/ORE.sh -execute 2 \${WORKSPACE}/\$finalPath \${WORKSPACE}/\$finalPath/master.xml
                    done
                fi
                """
                )
            }
            script.echo('################ Oracle Ruleengine Ended ##############################')
        } catch (all) {
            script.echo('Oracle RuleEngine Failed')
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }

            throw all
        }
    }
}
