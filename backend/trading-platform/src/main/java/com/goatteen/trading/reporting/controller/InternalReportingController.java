package com.goatteen.trading.reporting.controller;

import com.goatteen.trading.reporting.metrics.ActivityTrendPoint;
import com.goatteen.trading.reporting.metrics.ActivityTrendService;
import com.goatteen.trading.reporting.metrics.ClientMetrics;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;
import com.goatteen.trading.reporting.metrics.InstrumentMetrics;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;
import com.goatteen.trading.reporting.metrics.ReportingActivity;
import com.goatteen.trading.reporting.metrics.ReportingActivityService;
import com.goatteen.trading.reporting.metrics.TradingActivityMetrics;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/reporting")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'OPERATIONS')")
public class InternalReportingController {

    private final TradingActivityMetricsService tradingActivityService;
    private final ReportingActivityService activityService;
    private final InstrumentMetricsService instrumentService;
    private final ClientMetricsService clientService;
    private final ActivityTrendService trendService;

    public InternalReportingController(
            TradingActivityMetricsService tradingActivityService,
            ReportingActivityService activityService,
            InstrumentMetricsService instrumentService,
            ClientMetricsService clientService,
            ActivityTrendService trendService) {
        this.tradingActivityService = tradingActivityService;
        this.activityService = activityService;
        this.instrumentService = instrumentService;
        this.clientService = clientService;
        this.trendService = trendService;
    }

    @GetMapping("/summary")
    public TradingActivityMetrics summary(@ModelAttribute ReportingQuery query) {
        return tradingActivityService.calculate(query.toFilters());
    }

    @GetMapping("/activity")
    public List<ReportingActivity> activity(@ModelAttribute ReportingQuery query) {
        return activityService.find(query.toFilters());
    }

    @GetMapping("/instruments")
    public List<InstrumentMetrics> instruments(@ModelAttribute ReportingQuery query) {
        return instrumentService.calculate(query.toFilters());
    }

    @GetMapping("/clients")
    public List<ClientMetrics> clients(@ModelAttribute ReportingQuery query) {
        return clientService.calculate(query.toFilters());
    }

    @GetMapping("/trends")
    public List<ActivityTrendPoint> trends(@ModelAttribute ReportingQuery query) {
        return trendService.calculate(query.toFilters());
    }
}
