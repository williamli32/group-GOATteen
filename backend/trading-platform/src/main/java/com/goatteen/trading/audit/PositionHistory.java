package com.goatteen.trading.audit;

import com.goatteen.trading.execution.Fill;
import com.goatteen.trading.instrument.Instrument;
import com.goatteen.trading.account.Account;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit trail entity for position changes
 * Tracks every position change linked to a fill for complete reconstructability
 * 
 * Requirement: Make every completed or rejected trade fully reconstructable from persisted data
 */
@Entity
@Table(name = "position_history")
public class PositionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fill_id", nullable = false)
    private Fill fill;

    @Column(name = "quantity_before", nullable = false)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private BigDecimal quantityAfter;

    @Column(name = "quantity_change", nullable = false)
    private BigDecimal quantityChange;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    // Constructors
    protected PositionHistory() {
        // Required by JPA
    }

    public PositionHistory(Account account, Instrument instrument, Fill fill,
                          BigDecimal quantityBefore, BigDecimal quantityAfter) {
        this.account = account;
        this.instrument = instrument;
        this.fill = fill;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.quantityChange = quantityAfter.subtract(quantityBefore);
        this.recordedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public Fill getFill() {
        return fill;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public BigDecimal getQuantityChange() {
        return quantityChange;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    // Setters
    public void setAccount(Account account) {
        this.account = account;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }

    public void setFill(Fill fill) {
        this.fill = fill;
    }

    public void setQuantityBefore(BigDecimal quantityBefore) {
        this.quantityBefore = quantityBefore;
    }

    public void setQuantityAfter(BigDecimal quantityAfter) {
        this.quantityAfter = quantityAfter;
    }

    public void setQuantityChange(BigDecimal quantityChange) {
        this.quantityChange = quantityChange;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> 29270f9221c18597faef1b200a17581cbc923fd4
