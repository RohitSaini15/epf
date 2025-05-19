package com.cigna.ticket

/**
 * Exceptions used in CignaBuildFlowSpec to differentiate a step error vs a ticket open/close error
 */

class CloseTicketFailure extends Exception {
    CloseTicketFailure(String message) {
        super(message)
    }
}
