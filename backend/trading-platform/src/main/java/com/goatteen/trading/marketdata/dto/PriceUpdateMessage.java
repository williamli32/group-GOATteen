package com.goatteen.trading.marketdata.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Real-time price update message returned via REST API
 * Used for live price streaming to frontend
 */
public class PriceUpdateMessage {

    private String symbol;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private BigDecimal lastPrice;
    private BigDecimal midPrice;  // (bid + ask) / 2, useful for trading logic
    private LocalDateTime timestamp;
    private int sequenceNumber;  // For ensuring message ordering

    public PriceUpdateMessage() {
    }

    public PriceUpdateMessage(
            String symbol,
            BigDecimal bidPrice,
            BigDecimal askPrice,
            BigDecimal lastPrice,
            LocalDateTime timestamp,
            int sequenceNumber) {
        this.symbol = symbol;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.lastPrice = lastPrice;
        this.midPrice = bidPrice.add(askPrice).divide(java.math.BigDecimal.valueOf(2));
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(BigDecimal bidPrice) {
        this.bidPrice = bidPrice;
    }

    public BigDecimal getAskPrice() {
        return askPrice;
    }

    public void setAskPrice(BigDecimal askPrice) {
        this.askPrice = askPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getMidPrice() {
        return midPrice;
    }

    public void setMidPrice(BigDecimal midPrice) {
        this.midPrice = midPrice;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
}
