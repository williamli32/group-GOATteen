package com.goatteen.trading.audit;

public class TradeIntegrityReport {

    private final Long orderId;

    private final boolean valid;

    private final String message;

    public TradeIntegrityReport(
            Long orderId,
            boolean valid,
            String message) {

        this.orderId = orderId;
        this.valid = valid;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }
}