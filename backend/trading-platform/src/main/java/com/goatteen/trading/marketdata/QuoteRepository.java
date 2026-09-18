package com.goatteen.trading.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Optional;

public interface QuoteRepository
        extends JpaRepository<Quote, Long> {

    Optional<Quote> findTopByInstrumentIdOrderByQuotedAtDesc(
            Long instrumentId);

    /*
     * Delete old simulator ticks only when:
     *
     * 1. The quote is older than the retention cutoff.
     * 2. No fill references the quote.
     * 3. A newer quote exists for the same instrument.
     *
     * Therefore:
     * - trade audit quotes are preserved;
     * - the latest quote is always preserved;
     * - recent quote history is temporarily retained.
     */
    @Modifying
    @Query(value = """
            DELETE FROM market_quotes q
            WHERE q.quoted_at < :cutoff

              AND NOT EXISTS (
                  SELECT 1
                  FROM fills f
                  WHERE f.quote_id = q.id
              )

              AND EXISTS (
                  SELECT 1
                  FROM market_quotes newer
                  WHERE newer.instrument_id = q.instrument_id
                    AND (
                        newer.quoted_at > q.quoted_at
                        OR (
                            newer.quoted_at = q.quoted_at
                            AND newer.id > q.id
                        )
                    )
              )
            """, nativeQuery = true)
    int deleteUnusedHistoricalQuotesBefore(
            @Param("cutoff") LocalDateTime cutoff);

}