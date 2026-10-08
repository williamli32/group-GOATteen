package com.goatteen.trading.reporting.event;

import org.springframework.context.ApplicationEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Order Filled Event published when an order is successfully filled.

public class OrderFilledEvent extends ApplicationEvent{

    // Used private final to make the data immuatable
    private final Long fillId;
    private final Long orderId;
    private final Long accountId;
    private final Long clientId;
    private final Long instrumentId;
    private final String orderSide;
    // Using BigDecimal to ensure accuracy
    private final BigDecimal orderQuantity;
    private final BigDecimal executionPrice;
    private final BigDecimal executionQuantity;
    private final LocalDateTime orderSubmittedAt;
    private final LocalDateTime orderAcceptedAt;
    private final LocalDateTime orderFilledAt;
    private final String orderStatus;
    private final BigDecimal bidPrice;
    private final BigDecimal askPrice;
    private final LocalDateTime quoteTimestamp;

    public OrderFilledEvent (
        Object source,
        Long fillId,
        Long orderId,
        Long accountId,
        Long clientId,
        Long instrumentId,
        String orderSide,
        BigDecimal orderQuantity,
        BigDecimal executionPrice,
        BigDecimal executionQuantity,
        LocalDateTime orderSubmittedAt,
        LocalDateTime orderAcceptedAt,
        LocalDateTime orderFilledAt,
        String orderStatus,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        LocalDateTime quoteTimestamp
    ){
            super(source);
            this.fillId = fillId;
            this.orderId = orderId;
            this.accountId = accountId;
            this.clientId = clientId;
            this.instrumentId = instrumentId;
            this.orderSide = orderSide;
            this.orderQuantity = orderQuantity;
            this.executionPrice = executionPrice;
            this.executionQuantity = executionQuantity;
            this.orderSubmittedAt = orderSubmittedAt;
            this.orderAcceptedAt = orderAcceptedAt;
            this.orderFilledAt = orderFilledAt;
            this.orderStatus = orderStatus;
            this.bidPrice = bidPrice;
            this.askPrice = askPrice;
            this.quoteTimestamp = quoteTimestamp;
    }

}
