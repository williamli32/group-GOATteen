package com.goatteen.trading.order;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.instrument.Instrument;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "orders")
public class Order {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;


    @Column(nullable = false)
    private BigDecimal quantity;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;


    @Column(name = "rejection_reason")
    private String rejectionReason;


    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;


    @Column(name = "completed_at")
    private LocalDateTime completedAt;


    public Long getId() {
        return id;
    }


    public Account getAccount() {
        return account;
    }


    public Instrument getInstrument() {
        return instrument;
    }


    public OrderSide getSide() {
        return side;
    }


    public BigDecimal getQuantity() {
        return quantity;
    }


    public OrderStatus getStatus() {
        return status;
    }


    public String getRejectionReason() {
        return rejectionReason;
    }


    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }


    public LocalDateTime getCompletedAt() {
        return completedAt;
    }


    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }


    public void setStatus(OrderStatus status) {
        this.status = status;
    }


    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }


    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }


    public void setAccount(Account account) {
        this.account = account;
    }


    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }


    public void setSide(OrderSide side) {
        this.side = side;
    }


    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
