package com.cigna.ticket

/**
 * Exceptions used in CignaBuildFlowSpec to differentiate a step error vs a ticket open/close error
 */

class OpenTicketFailure extends Exception {
    OpenTicketFailure(String message) {
        super(message)
    }
}
