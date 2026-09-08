package com.goatteen.trading.account;


import com.goatteen.trading.client.Client;

import jakarta.persistence.*;

import java.math.BigDecimal;


@Entity
@Table(name = "accounts")
public class Account {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Version
    private Long version;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "client_id",
        nullable = false,
        unique = true
    )
    private Client client;


    @Column(
        name = "account_number",
        nullable = false,
        unique = true
    )
    private String accountNumber;


    @Column(
        name = "cash_balance",
        nullable = false
    )
    private BigDecimal cashBalance;


    @Column(nullable = false)
    private String currency;


    public Long getId() {
        return id;
    }


    public Long getVersion() {
        return version;
    }


    public Client getClient() {
        return client;
    }


    public String getAccountNumber() {
        return accountNumber;
    }


    public BigDecimal getCashBalance() {
        return cashBalance;
    }


    public String getCurrency() {
        return currency;
    }


    public void setCashBalance(java.math.BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }
}