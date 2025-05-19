package com.cigna.common.exception

/**
 * This exception should be thrown if the user input is invalid
 *  @Param message The detail message is saved for later retrieval by the Throwable.getMessage() method.
 */
class InvalidInputException extends Exception {
    InvalidInputException(String message) {
        super(message)
    }
}
