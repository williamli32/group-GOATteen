package com.goatteen.trading.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;


public class HoldingsResponse {

    private Long accountId;
    private BigDecimal cashBalance;
    private String currency;
    private List<PositionResponse> positions;

    public HoldingsResponse() {
    }

    public HoldingsResponse(Long accountId, BigDecimal cashBalance, String currency, List<PositionResponse> positions) {
        this.accountId = accountId;
        this.cashBalance = cashBalance;
        this.currency = currency;
        this.positions = positions;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<PositionResponse> getPositions() {
        return positions;
    }

    public void setPositions(List<PositionResponse> positions) {
        this.positions = positions;
    }
}
