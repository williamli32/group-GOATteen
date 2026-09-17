package com.goatteen.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MarketData {
    private String symbol;
    private String market;
    private String name;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private BigDecimal high;
    private BigDecimal low;
    private Long volume;
    private LocalDateTime timestamp;

    // No-args constructor
    public MarketData() {
    }

    // All-args constructor
    public MarketData(String symbol, String market, String name, BigDecimal price, 
                     BigDecimal change, BigDecimal changePercent, BigDecimal high, 
                     BigDecimal low, Long volume, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.market = market;
        this.name = name;
        this.price = price;
        this.change = change;
        this.changePercent = changePercent;
        this.high = high;
        this.low = low;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    // Getters
    public String getSymbol() { return symbol; }
    public String getMarket() { return market; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getChange() { return change; }
    public BigDecimal getChangePercent() { return changePercent; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public Long getVolume() { return volume; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // Setters
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setMarket(String market) { this.market = market; }
    public void setName(String name) { this.name = name; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setChange(BigDecimal change) { this.change = change; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    public void setHigh(BigDecimal high) { this.high = high; }
    public void setLow(BigDecimal low) { this.low = low; }
    public void setVolume(Long volume) { this.volume = volume; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}