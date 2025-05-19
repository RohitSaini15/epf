package com.cigna.common.compliance

/**
 *  Calculates random UUID as correlation ID for compliance checks
 */
class DefaultStrategy extends CorrelationStrategy {
    protected DefaultStrategy() { }

    @Override
    String getCorrelationID() {
        UUID.randomUUID()
    }
}
