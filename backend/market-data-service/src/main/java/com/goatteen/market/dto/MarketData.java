package com.goatteen.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MarketData {

    private String assetType;

    private String symbol;

    private String exchange;

    private String countryCode;

    private String currency;

    /*
     * Temporary backwards-compatible field.
     *
     * For now this mirrors countryCode because
     * the experimental Angular UI already uses it.
     *
     * New filtering should use assetType.
     */
    private String market;

    private String name;

    private BigDecimal price;

    private BigDecimal change;

    private BigDecimal changePercent;

    private BigDecimal high;

    private BigDecimal low;

    private Long volume;

    private LocalDateTime timestamp;

    public MarketData() {
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(
            String assetType) {

        this.assetType = assetType;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(
            String symbol) {

        this.symbol = symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(
            String exchange) {

        this.exchange = exchange;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(
            String countryCode) {

        this.countryCode = countryCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(
            String currency) {

        this.currency = currency;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(
            String market) {

        this.market = market;
    }

    public String getName() {
        return name;
    }

    public void setName(
            String name) {

        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(
            BigDecimal price) {

        this.price = price;
    }

    public BigDecimal getChange() {
        return change;
    }

    public void setChange(
            BigDecimal change) {

        this.change = change;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(
            BigDecimal changePercent) {

        this.changePercent = changePercent;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(
            BigDecimal high) {

        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(
            BigDecimal low) {

        this.low = low;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(
            Long volume) {

        this.volume = volume;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(
            LocalDateTime timestamp) {

        this.timestamp = timestamp;
    }
}