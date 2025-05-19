package com.cigna.common.compliance

/**
 *  Accepts user input as correlation ID for compliance checks
 */
class UserDefinedStrategy extends CorrelationStrategy {
    protected Map<String, Object> config

    protected UserDefinedStrategy(Map<String, Object> config) {
        this.config = config
    }

    @Override
    String getCorrelationID() {
        config?.customCorrelationID
    }
}
