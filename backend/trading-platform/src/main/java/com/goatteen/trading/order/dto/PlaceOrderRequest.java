package com.goatteen.trading.order.dto;

import com.goatteen.trading.order.OrderSide;

import java.math.BigDecimal;


public class PlaceOrderRequest {

    private Long accountId;
    private Long instrumentId;
    private OrderSide side;
    private BigDecimal quantity;

    public PlaceOrderRequest() {
    }

    public PlaceOrderRequest(Long accountId, Long instrumentId, OrderSide side, BigDecimal quantity) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.side = side;
        this.quantity = quantity;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
