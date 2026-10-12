
package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.reporting.data.TradeFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InstrumentMetricsService {

    private final TradeFactRepository tradeFactRepository;

    public InstrumentMetricsService(
            TradeFactRepository tradeFactRepository) {
        this.tradeFactRepository = tradeFactRepository;
    }

    @Transactional(readOnly = true)
    public List<InstrumentMetrics> calculate(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (startDate == null || endDate == null ||
                !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException(
                    "Start date must be before end date");
        }

        return tradeFactRepository
                .summarizeByInstrument(startDate, endDate)
                .stream()
                .map(row -> new InstrumentMetrics(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue(),
                        (BigDecimal) row[4],
                        (BigDecimal) row[5]
                ))
                .toList();
    }
}
