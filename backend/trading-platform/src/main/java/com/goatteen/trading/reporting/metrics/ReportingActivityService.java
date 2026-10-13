package com.goatteen.trading.reporting.metrics;

import com.goatteen.trading.reporting.data.TradeFactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportingActivityService {

    private final TradeFactRepository tradeFactRepository;

    public ReportingActivityService(TradeFactRepository tradeFactRepository) {
        this.tradeFactRepository = tradeFactRepository;
    }

    @Transactional(readOnly = true)
    public List<ReportingActivity> find(ReportingFilters filters) {
        return tradeFactRepository.findActivity(
                        filters.fromDate(),
                        filters.toDate(),
                        filters.instrumentId(),
                        filters.side(),
                        filters.status(),
                        filters.clientId(),
                        filters.accountId())
                .stream()
                .map(row -> new ReportingActivity(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue(),
                        (String) row[5],
                        (String) row[6],
                        (String) row[7],
                        (java.math.BigDecimal) row[8],
                        (java.math.BigDecimal) row[9],
                        (java.math.BigDecimal) row[10],
                        (java.math.BigDecimal) row[11],
                        ((java.time.LocalDateTime) row[12])))
                .toList();
    }
}
