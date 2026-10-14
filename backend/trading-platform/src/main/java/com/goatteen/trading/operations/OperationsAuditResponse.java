package com.goatteen.trading.operations;

import com.goatteen.trading.audit.TradeAuditResponse;
import com.goatteen.trading.audit.TradeIntegrityReport;

public record OperationsAuditResponse(
        TradeAuditResponse evidence,
        TradeIntegrityReport integrity,
        boolean pending) {
}
