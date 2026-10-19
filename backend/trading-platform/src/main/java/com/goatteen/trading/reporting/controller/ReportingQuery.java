package com.goatteen.trading.reporting.controller;

import com.goatteen.trading.reporting.metrics.ReportingFilters;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ReportingQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private Long instrumentId;
    private String side;
    private String status;
    private Long clientId;
    private Long accountId;
    private String currency;

    public ReportingFilters toFilters() {

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "fromDate and toDate are required");
        }

        return new ReportingFilters(
            fromDate.atStartOfDay(),
            toDate.plusDays(1).atStartOfDay(),
            instrumentId,
            side,
            status,
            clientId,
            accountId,
            currency);
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
