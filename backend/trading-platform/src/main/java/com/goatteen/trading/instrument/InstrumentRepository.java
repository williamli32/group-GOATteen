package com.goatteen.trading.instrument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository
        extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findBySymbol(
            String symbol);

    List<Instrument> findByTradableTrueOrderBySymbolAsc();
}