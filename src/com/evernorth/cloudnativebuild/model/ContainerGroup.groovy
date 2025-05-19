package com.evernorth.cloudnativebuild.model

import com.cigna.common.kubernetes.PodConfigGenerator
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.service.ModuleUtil
import com.evernorth.cloudnativebuild.service.PipelineStateManager

class ContainerGroup implements Serializable{
    String containerName
    List<StepInvocation> steps

    @NonCPS
    static String findContainerNameForStep(StepInvocation step, PipelineStateManager manager){
        if(step.verb== PipelineConstants.VERB_RUN_SCRIPT){
            String imageName = (step?.arguments?.dockerImage) ? step?.arguments?.dockerImage : manager.configuration.defaultDockerImage
            return PodConfigGenerator.getContainerName(imageName)
        }
        if(ModuleUtil.isNotShellModule(step.verb)){
            return PodConfigGenerator.getContainerName(manager.configuration.defaultDockerImage)
        }
        ModuleContractType moduleContractType = ModuleUtil.getContractTypeForVerb(step.verb)
        def contract = manager.getContractByTypeAndModuleName(moduleContractType, step.options?.get('filter') as String)
        if(!contract){
            return ""
        }
        return PodConfigGenerator.getContainerName(contract.image)
    }

    /**
     * Divide steps in sub-collections based on container required. This will allow several steps
     * to run inside of single container closure rather then creating a container closure for each
     * @param steps
     * @return
     */
    @NonCPS
    static List<ContainerGroup> CreateContainerGroupsFromSubPlan(PipelineStateManager manager, List <StepInvocation> steps){
        List<ContainerGroup> containerGroups = []
        String lastContainer=""
        int groupNum=-1
        steps.each {
            String containerName = findContainerNameForStep(it, manager)
            if(containerName==""){
                return containerGroups
            }
            if(lastContainer!=containerName){
                containerGroups.add(new ContainerGroup(containerName: containerName,steps:[]))
                lastContainer=containerName
                groupNum++
            }
            containerGroups[groupNum].steps.add(it)
        }
        return containerGroups
    }
}
