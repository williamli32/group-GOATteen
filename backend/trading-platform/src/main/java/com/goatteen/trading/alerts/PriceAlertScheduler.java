package com.goatteen.trading.alerts;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Separates scheduled polling from the transactional, directly testable evaluator. */
@Component
@ConditionalOnProperty(prefix = "app.price-alerts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PriceAlertScheduler {
    private final PriceAlertEvaluator evaluator;
    public PriceAlertScheduler(PriceAlertEvaluator evaluator) { this.evaluator = evaluator; }

    @Scheduled(fixedDelayString = "${app.price-alerts.check-ms:3000}",
            initialDelayString = "${app.price-alerts.initial-delay-ms:5000}")
    public void poll() { evaluator.evaluate(); }
}
