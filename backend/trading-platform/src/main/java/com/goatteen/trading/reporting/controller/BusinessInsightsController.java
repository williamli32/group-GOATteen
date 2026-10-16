package com.goatteen.trading.reporting.controller;

import com.goatteen.trading.reporting.metrics.BusinessInsights;
import com.goatteen.trading.reporting.metrics.BusinessInsightsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/reporting")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
public class BusinessInsightsController {
    private final BusinessInsightsService insightsService;

    public BusinessInsightsController(BusinessInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/insights")
    public BusinessInsights insights(@ModelAttribute ReportingQuery query) {
        return insightsService.calculate(query.toFilters());
    }
}
