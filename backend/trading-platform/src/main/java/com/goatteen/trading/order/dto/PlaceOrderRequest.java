package com.goatteen.trading.order.dto;

import com.goatteen.trading.order.OrderSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class PlaceOrderRequest {

    @NotNull(message = "Instrument is required")
    @Positive(message = "Instrument ID must be positive")
    private Long instrumentId;

    @NotNull(message = "Order side is required")
    private OrderSide side;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    public PlaceOrderRequest() {
    }

    public PlaceOrderRequest(
            Long instrumentId,
            OrderSide side,
            BigDecimal quantity) {
        this.instrumentId = instrumentId;
        this.side = side;
        this.quantity = quantity;
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