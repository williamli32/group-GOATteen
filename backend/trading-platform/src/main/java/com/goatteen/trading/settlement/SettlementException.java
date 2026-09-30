package com.goatteen.trading.settlement;

/**
 * Custom exception thrown during settlement execution failures
 */
public class SettlementException extends RuntimeException {

    public SettlementException(String message) {
        super(message);
    }

    public SettlementException(String message, Throwable cause) {
        super(message, cause);
    }
}