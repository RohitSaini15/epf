import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.pipeline.steps.CallbackStep

def call() {
    // To aid in debugging release/preRelease workflows, flip to true
    FeatureFlags.debug = FeatureFlags.verbose = FeatureFlags.showStackTraces = false
    sendSplunkConsoleLog {
        String banner = libraryResource('com/cigna/banners/bannerStart.txt')
        echo(banner)
        timestamps {
            PipelineStateContext psc
            // necessary for injecting PSC from tests
            if (!binding.hasVariable('psc')) {
                // enable extra logging by default, as any failure in callback jobs is not detailed and increases time to identify the issue
                def defaultConfig = [featureFlags: [verbose: true,
                                                    debug: true,
                                                    showStackTraces: true]]
                psc = Utils.PipelineStateContext(this, defaultConfig)
            } else {
                psc = binding.getVariable('psc') as PipelineStateContext
            }
            echo(FeatureFlags.asString())
            CallbackStep step = new CallbackStep(psc, this)
            step.execute()
        }
    }
}
