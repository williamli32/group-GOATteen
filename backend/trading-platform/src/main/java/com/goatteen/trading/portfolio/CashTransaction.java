package com.goatteen.trading.portfolio;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.execution.Fill;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "cash_transactions")
public class CashTransaction {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fill_id")
    private Fill fill;


    @Column(nullable = false)
    private BigDecimal amount;


    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    @Column
    private String description;


    public Long getId() {
        return id;
    }


    public Account getAccount() {
        return account;
    }


    public Fill getFill() {
        return fill;
    }


    public BigDecimal getAmount() {
        return amount;
    }


    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public String getDescription() {
        return description;
    }
}
