package com.goatteen.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class HistoricalPrice {
    private String symbol;
    private LocalDate date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    // No-args constructor
    public HistoricalPrice() {
    }

    // All-args constructor
    public HistoricalPrice(String symbol, LocalDate date, BigDecimal open, BigDecimal high,
                          BigDecimal low, BigDecimal close, Long volume) {
        this.symbol = symbol;
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // Getters
    public String getSymbol() {
        return symbol;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public Long getVolume() {
        return volume;
    }

    // Setters
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }
}
