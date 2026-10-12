
package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.reporting.data.TradeFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientMetricsService {

    private final TradeFactRepository tradeFactRepository;

    public ClientMetricsService(
            TradeFactRepository tradeFactRepository) {
        this.tradeFactRepository = tradeFactRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientMetrics> calculate(
            LocalDateTime startDate,
            LocalDateTime endDate) {

        if (startDate == null || endDate == null ||
                !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException(
                    "Start date must be before end date");
        }

        return tradeFactRepository
                .summarizeByClient(startDate, endDate)
                .stream()
                .map(row -> new ClientMetrics(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3],
                        (BigDecimal) row[4]
                ))
                .toList();
    }
}
