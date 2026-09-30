package com.goatteen.trading.order;

/**
 * Custom exception thrown when an invalid order state transition is attempted
 * 
 * Example: Trying to move from SETTLED → SETTLING would throw this exception
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }

    public InvalidOrderStateException(String message, Throwable cause) {
        super(message, cause);
    }
}