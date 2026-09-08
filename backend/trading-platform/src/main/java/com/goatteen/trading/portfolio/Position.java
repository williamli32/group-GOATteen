package com.goatteen.trading.portfolio;

import com.goatteen.trading.account.Account;
import com.goatteen.trading.instrument.Instrument;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(
    name = "positions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "instrument_id"})
)
public class Position {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Version
    private Long version;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;


    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;


    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public Long getId() {
        return id;
    }


    public Long getVersion() {
        return version;
    }


    public Account getAccount() {
        return account;
    }


    public Instrument getInstrument() {
        return instrument;
    }


    public BigDecimal getQuantity() {
        return quantity;
    }


    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }


    public void setQuantity(java.math.BigDecimal quantity) {
        this.quantity = quantity;
    }


    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }


    public void setAccount(Account account) {
        this.account = account;
    }


    public void setInstrument(com.goatteen.trading.instrument.Instrument instrument) {
        this.instrument = instrument;
    }
}
