package com.goatteen.trading.portfolio.dto;

import java.math.BigDecimal;


public class PositionResponse {

    private Long instrumentId;
    private String symbol;
    private String instrumentName;
    private BigDecimal quantity;

    public PositionResponse() {
    }

    public PositionResponse(Long instrumentId, String symbol, String instrumentName, BigDecimal quantity) {
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
