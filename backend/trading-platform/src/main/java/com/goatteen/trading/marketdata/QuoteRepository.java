package com.goatteen.trading.marketdata;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface QuoteRepository
        extends JpaRepository<Quote, Long> {


    Optional<Quote> findTopByInstrumentIdOrderByQuotedAtDesc(Long instrumentId);

}
