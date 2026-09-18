package com.goatteen.trading.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(prefix = "app.market-simulator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimulatedQuoteRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(
            SimulatedQuoteRetentionService.class);

    private final QuoteRepository quoteRepository;

    private final long retentionMinutes;

    public SimulatedQuoteRetentionService(
            QuoteRepository quoteRepository,

            @Value("${app.market-simulator.quote-retention-minutes:2}") long retentionMinutes) {

        this.quoteRepository = quoteRepository;

        this.retentionMinutes = retentionMinutes;
    }

    @Scheduled(fixedDelayString = "${app.market-simulator.cleanup-ms:60000}",

            initialDelayString = "${app.market-simulator.cleanup-initial-delay-ms:15000}")
    @Transactional
    public void cleanOldQuotes() {

        LocalDateTime cutoff = LocalDateTime.now()
                .minusMinutes(
                        retentionMinutes);

        int deleted = quoteRepository
                .deleteUnusedHistoricalQuotesBefore(
                        cutoff);

        if (deleted > 0) {

            logger.info(
                    "Removed {} old unused simulated market quotes",
                    deleted);
        }
    }
}