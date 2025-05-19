package com.cigna.common.compliance

/**
 * Determines and calculates correlation ID for compliance checks based on input
 */
abstract class CorrelationStrategy {
    protected CorrelationStrategy() { }

    abstract String getCorrelationID()
}
