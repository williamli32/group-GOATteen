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

        return calculate(
                ReportingFilters.forPeriod(
                        startDate,
                        endDate));
    }

    @Transactional(readOnly = true)
    public List<InstrumentMetrics> calculate(
            ReportingFilters filters) {

        return tradeFactRepository
                .summarizeByInstrument(
                        filters.fromDate(),
                        filters.toDate(),
                        filters.instrumentId(),
                        filters.side(),
                        filters.status(),
                        filters.clientId(),
                        filters.accountId(),
                        filters.currency())
                .stream()
                .map(
                        row -> new InstrumentMetrics(
                                (Long) row[0],
                                (String) row[1],
                                (String) row[2],
                                ((Number) row[3])
                                        .longValue(),
                                (BigDecimal) row[4],
                                (BigDecimal) row[5]))
                .toList();
    }
}