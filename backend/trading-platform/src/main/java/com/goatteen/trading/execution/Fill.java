package com.goatteen.trading.execution;

import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.order.Order;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fills")
public class Fill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "fill_price", nullable = false)
    private BigDecimal fillPrice;

    @Column(name = "fill_quantity", nullable = false)
    private BigDecimal fillQuantity;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "fx_source_currency", nullable = false, length = 3)
    private String fxSourceCurrency;

    @Column(name = "fx_target_currency", nullable = false, length = 3)
    private String fxTargetCurrency;

    @Column(name = "fx_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal fxRate;

    @Column(name = "fx_rate_date", nullable = false)
    private LocalDate fxRateDate;

    @Column(name = "fx_provider", nullable = false, length = 40)
    private String fxProvider;

    @Column(name = "cash_settlement_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal cashSettlementAmount;

    public String getFxSourceCurrency() { return fxSourceCurrency; }
    public void setFxSourceCurrency(String value) { fxSourceCurrency = value; }
    public String getFxTargetCurrency() { return fxTargetCurrency; }
    public void setFxTargetCurrency(String value) { fxTargetCurrency = value; }
    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal value) { fxRate = value; }
    public LocalDate getFxRateDate() { return fxRateDate; }
    public void setFxRateDate(LocalDate value) { fxRateDate = value; }
    public String getFxProvider() { return fxProvider; }
    public void setFxProvider(String value) { fxProvider = value; }
    public BigDecimal getCashSettlementAmount() { return cashSettlementAmount; }
    public void setCashSettlementAmount(BigDecimal value) { cashSettlementAmount = value; }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(
            String idempotencyKey) {

        this.idempotencyKey = idempotencyKey;
    }

    public Quote getQuote() {
        return quote;
    }

    public BigDecimal getFillPrice() {
        return fillPrice;
    }

    public BigDecimal getFillQuantity() {
        return fillQuantity;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public void setQuote(Quote quote) {
        this.quote = quote;
    }

    public void setFillPrice(java.math.BigDecimal fillPrice) {
        this.fillPrice = fillPrice;
    }

    public void setFillQuantity(java.math.BigDecimal fillQuantity) {
        this.fillQuantity = fillQuantity;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
