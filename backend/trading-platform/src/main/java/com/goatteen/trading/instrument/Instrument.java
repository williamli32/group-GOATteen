package com.goatteen.trading.instrument;

import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "instruments")
public class Instrument {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, unique = true)
    private String symbol;


    @Column(nullable = false)
    private String name;


    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_class", nullable = false)
    private InstrumentClass instrumentClass;


    @Column(nullable = false)
    private String currency;


    @Column(nullable = false)
    private boolean tradable = true;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


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
