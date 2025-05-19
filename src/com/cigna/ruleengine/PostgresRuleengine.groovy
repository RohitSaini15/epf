package com.cigna.ruleengine

import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 *  class that defines the ruleengine
 */
class PostgresRuleengine extends Ruleengine {
    PostgresRuleengine() {
        containerName = 'ruleenginevuni_10'
        containerImage = 'enterprise-devops/ruleengine'
        containerVersion = 'uni_1.0'
        containerCpu = '2000m'
        containerMemory = '2000Mi'
        baseValidationItems = [
            'ruleengineType',
            'postgres.pathForBuildXml',
            'postgres.unfiedEngineType',
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
        String file = config.postgres?.pathForBuildXml ?: "${script.env.WORKSPACE}/src/build.xml"
        String unfiedEngineType = config.postgres?.unfiedEngineType ?: 'PG_RE'
        String INI_FILE = "${script.env.WORKSPACE}/PyDream.ini"
        String LOG_FILE = "${script.env.WORKSPACE}/Rules_Engine.log"
        String basePath = ''
        script.container(containerName) {
            basePath = script.sh(
                script: "set +x | sed s/build.xml// <<<${file} | tr -d \"\\n\\t\\r\"",
                returnStdout: true
            )
            script.echo("${basePath}")
            script.echo('################ Postgres Ruleengine Started ############################')
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
                    cat \${basePath}result.xml
                """
            )
            script.sh(
                script: """ set +x
                    export UN_RE_DMV_PSWD='RE01!?pgpw'
                    echo \$UN_RE_DMV_PSWD
                    echo "BasePath : ${basePath}"
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
                        for i in \${lines[@]}
                        do
                            echo \$i
                            subString=`echo "\$i" | awk -F'"' '{print \$2 }'`
                            echo \$subString
                        done
                        cd /app
                        echo "[UN_RE]"                                          >  ${INI_FILE}
                        echo "PARALLEL_DEGREE=8"                                >> ${INI_FILE}
                        echo "VERBOSE=1"                                        >> ${INI_FILE}
                        echo "INPUT_SQL_DIR=${basePath}\$subString"             >> ${INI_FILE}
                        echo "RULES_ENGINE_TYPE=${unfiedEngineType}"            >> ${INI_FILE}
                        echo "################ Start PyDream.ini ############################"
                        cat ${INI_FILE}
                        echo "################ End PyDream.ini ############################"
                        echo "############################## END ############################"
                        python3.9 --version
                        ls -la /app/bin
                        python3.9 -B /app/UN_RE -i ${INI_FILE} -v | tee ${LOG_FILE}
                    fi
                    """
            )
            script.echo('################ Postgres Ruleengine Ended ##############')
        }
    }
}
