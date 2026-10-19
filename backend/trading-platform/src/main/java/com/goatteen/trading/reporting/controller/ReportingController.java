
package com.goatteen.trading.reporting.controller;

import com.goatteen.trading.reporting.metrics.ActivityTrendPoint;
import com.goatteen.trading.reporting.metrics.ActivityTrendService;

import com.goatteen.trading.reporting.metrics.ClientMetrics;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;
import com.goatteen.trading.reporting.metrics.InstrumentMetrics;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;
import com.goatteen.trading.reporting.metrics.PeriodicReport;
import com.goatteen.trading.reporting.metrics.PeriodicReportingService;
import com.goatteen.trading.reporting.metrics.ReportingPeriod;
import com.goatteen.trading.reporting.metrics.TradingActivityMetrics;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reporting")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST', 'OPERATIONS')")
public class ReportingController {

    private final TradingActivityMetricsService tradingService;
    private final InstrumentMetricsService instrumentService;
    private final ClientMetricsService clientService;
    private final PeriodicReportingService periodicService;
    private final ActivityTrendService activityTrendService;

    public ReportingController(
            TradingActivityMetricsService tradingService,
            InstrumentMetricsService instrumentService,
            ClientMetricsService clientService,
            PeriodicReportingService periodicService,
            ActivityTrendService activityTrendService) {

        this.tradingService = tradingService;
        this.instrumentService = instrumentService;
        this.clientService = clientService;
        this.periodicService = periodicService;
        this.activityTrendService = activityTrendService;
    }

    @GetMapping("/trends")
    public List<ActivityTrendPoint> trends(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {

        return activityTrendService.calculate(
                startDate,
                endDate);
    }

    @GetMapping("/trading")
    public TradingActivityMetrics trading(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {

        return tradingService.calculate(startDate, endDate);
    }

    @GetMapping("/instruments")
    public List<InstrumentMetrics> instruments(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {

        return instrumentService.calculate(startDate, endDate);
    }

    @GetMapping("/clients")
    public List<ClientMetrics> clients(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {

        return clientService.calculate(startDate, endDate);
    }

    @GetMapping("/periodic")
    public PeriodicReport periodic(
            @RequestParam ReportingPeriod period,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return periodicService.generate(period, date);
    }
}
