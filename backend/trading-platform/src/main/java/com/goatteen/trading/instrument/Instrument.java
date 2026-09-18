package com.goatteen.trading.instrument;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "instruments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_instruments_symbol_exchange", columnNames = {
                "symbol",
                "exchange"
        })
})
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_class", nullable = false)
    private InstrumentClass instrumentClass;

    @Column(nullable = false)
    private String exchange;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private boolean tradable = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Instrument() {
    }

    public Instrument(
            String symbol,
            String name,
            InstrumentClass instrumentClass,
            String exchange,
            String countryCode,
            String currency) {

        this.symbol = symbol;
        this.name = name;
        this.instrumentClass = instrumentClass;
        this.exchange = exchange;
        this.countryCode = countryCode;
        this.currency = currency;
        this.tradable = true;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public InstrumentClass getInstrumentClass() {
        return instrumentClass;
    }

    public String getExchange() {
        return exchange;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isTradable() {
        return tradable;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}