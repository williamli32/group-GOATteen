package com.goatteen.trading.marketdata;

import com.goatteen.trading.instrument.Instrument;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "market_quotes")
public class Quote {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "instrument_id",
        nullable = false,
        unique = true
    )
    private Instrument instrument;


    @Column(name = "bid_price", nullable = false)
    private BigDecimal bidPrice;


    @Column(name = "ask_price", nullable = false)
    private BigDecimal askPrice;


    @Column(name = "last_price", nullable = false)
    private BigDecimal lastPrice;


    @Column(name = "quoted_at", nullable = false)
    private LocalDateTime quotedAt;


    public Long getId() {
        return id;
    }


    public Instrument getInstrument() {
        return instrument;
    }


    public BigDecimal getBidPrice() {
        return bidPrice;
    }


    public BigDecimal getAskPrice() {
        return askPrice;
    }


    public BigDecimal getLastPrice() {
        return lastPrice;
    }


    public LocalDateTime getQuotedAt() {
        return quotedAt;
    }
}
