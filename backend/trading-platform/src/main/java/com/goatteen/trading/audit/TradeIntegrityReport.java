package com.goatteen.trading.audit;

/**
 * Trade Integrity Report
 * 
 * Verifies that a trade has all required audit trail data for complete reconstruction
 */
public class TradeIntegrityReport {
    private final Long orderId;
    private final boolean isValid;
    private final String message;

    public TradeIntegrityReport(Long orderId, boolean isValid, String message) {
        this.orderId = orderId;
        this.isValid = isValid;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public boolean isValid() {
        return isValid;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "TradeIntegrityReport{" +
                "orderId=" + orderId +
                ", isValid=" + isValid +
                ", message='" + message + '\'' +
                '}';
    }
}
