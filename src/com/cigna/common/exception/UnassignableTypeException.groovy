package com.cigna.common.exception

/**
 * This exception should be thrown if the user input is invalid
 *  @Param message tThe detail message is saved for later retrieval by the Throwable.getMessage() method.
 */
class UnassignableTypeException extends Exception {
    UnassignableTypeException(String message) {
        super(message)
    }
}
