package com.cigna.base

import com.cigna.state.PipelineStateContext

/**
 * Base class for all parts of The Pipeline that require the config Map and Jenkins Pipeline Object.
 */
abstract class CignaBuildFlowPipelineLib implements Serializable {
    protected def script
    protected Map config
    protected PipelineStateContext psc
}
