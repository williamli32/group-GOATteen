package com.goatteen.trading.audit;

<<<<<<< HEAD
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
=======
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
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public boolean isValid() {
<<<<<<< HEAD
        return isValid;
=======
        return valid;
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
    }

    public String getMessage() {
        return message;
    }
<<<<<<< HEAD

    @Override
    public String toString() {
        return "TradeIntegrityReport{" +
                "orderId=" + orderId +
                ", isValid=" + isValid +
                ", message='" + message + '\'' +
                '}';
    }
}
=======
}
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
