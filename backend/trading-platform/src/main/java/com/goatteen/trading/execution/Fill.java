package com.goatteen.trading.execution;

import com.goatteen.trading.marketdata.Quote;
import com.goatteen.trading.order.Order;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "fills")
public class Fill {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        unique = true
    )
    private Order order;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id")
    private Quote quote;


    @Column(name = "fill_price", nullable = false)
    private BigDecimal fillPrice;


    @Column(name = "fill_quantity", nullable = false)
    private BigDecimal fillQuantity;


    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;


    public Long getId() {
        return id;
    }


    public Order getOrder() {
        return order;
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
