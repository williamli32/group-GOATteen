package com.goatteen.trading.reporting.sync;


// Custom exception for reporting data synchronization failures.

public class SyncException extends RuntimeException {

    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyncException(Throwable cause) {
        super(cause);
    }
}
