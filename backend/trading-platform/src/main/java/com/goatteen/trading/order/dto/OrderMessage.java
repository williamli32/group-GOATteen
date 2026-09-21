package com.goatteen.trading.order.dto;

import java.math.BigDecimal;

/**
 * Message sent from frontend to place a trading order
 * Used with WebSocket /app/order/buy or /app/order/sell
 */
public class OrderMessage {

    private String symbol;
    private BigDecimal quantity;
    private BigDecimal limitPrice;  // null = market order, set = limit order
    private String orderType;  // "BUY" or "SELL"
    private Long accountId;

    public OrderMessage() {
    }

    public OrderMessage(
            String symbol,
            BigDecimal quantity,
            BigDecimal limitPrice,
            String orderType,
            Long accountId) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.orderType = orderType;
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
