package com.cigna.common.exception

/**
 * This exception should be thrown if the user ErrorStep is invalid
 *  @Param message tThe detail message is saved for later retrieval by the Throwable.getMessage() method.
 */
class ErrorStepException extends Exception {
    ErrorStepException(String message) {
        super(message)
    }
}
