package com.goatteen.trading.operations;

import com.goatteen.trading.order.OrderSide;
import com.goatteen.trading.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OperationsOrderResponse(
        Long orderId,
        Long clientId,
        String clientName,
        Long accountId,
        String accountNumber,
        Long instrumentId,
        String instrumentSymbol,
        OrderSide side,
        OrderStatus status,
        BigDecimal quantity,
        LocalDateTime submittedAt) {
}
