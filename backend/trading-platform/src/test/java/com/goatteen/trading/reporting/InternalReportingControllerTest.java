package com.goatteen.trading.reporting.tests;

import com.goatteen.trading.reporting.controller.InternalReportingController;
import com.goatteen.trading.reporting.controller.ReportingQuery;
import com.goatteen.trading.reporting.metrics.ActivityTrendService;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;
import com.goatteen.trading.reporting.metrics.ReportingActivityService;
import com.goatteen.trading.reporting.metrics.ReportingFilters;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalReportingControllerTest {

    @Test
    void queryConvertsDateFiltersToInclusiveDayRange() {
        ReportingQuery query = new ReportingQuery();
        query.setFromDate(LocalDate.of(2026, 9, 1));
        query.setToDate(LocalDate.of(2026, 9, 30));
        query.setInstrumentId(1L);
        query.setSide("buy");
        query.setStatus("filled");
        query.setClientId(2L);
        query.setAccountId(3L);

        ReportingFilters filters = query.toFilters();

        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(), filters.fromDate());
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(), filters.toDate());
        assertEquals("BUY", filters.side());
        assertEquals("FILLED", filters.status());
    }

    @Test
    void summaryDelegatesWithAllFilters() {
        TradingActivityMetricsService tradingService = mock(TradingActivityMetricsService.class);
        ReportingActivityService activityService = mock(ReportingActivityService.class);
        InstrumentMetricsService instrumentService = mock(InstrumentMetricsService.class);
        ClientMetricsService clientService = mock(ClientMetricsService.class);
        ActivityTrendService trendService = mock(ActivityTrendService.class);
        InternalReportingController controller = new InternalReportingController(
                tradingService, activityService, instrumentService, clientService, trendService);

        ReportingQuery query = new ReportingQuery();
        query.setFromDate(LocalDate.of(2026, 9, 1));
        query.setToDate(LocalDate.of(2026, 9, 30));
        query.setInstrumentId(1L);
        query.setSide("BUY");
        query.setStatus("FILLED");
        query.setClientId(2L);
        query.setAccountId(3L);

        controller.summary(query);

        verify(tradingService).calculate(query.toFilters());
    }

    @Test
    void exposesActivityInstrumentClientAndTrendQueries() {
        TradingActivityMetricsService tradingService = mock(TradingActivityMetricsService.class);
        ReportingActivityService activityService = mock(ReportingActivityService.class);
        InstrumentMetricsService instrumentService = mock(InstrumentMetricsService.class);
        ClientMetricsService clientService = mock(ClientMetricsService.class);
        ActivityTrendService trendService = mock(ActivityTrendService.class);
        InternalReportingController controller = new InternalReportingController(
                tradingService, activityService, instrumentService, clientService, trendService);

        ReportingQuery query = new ReportingQuery();
        query.setFromDate(LocalDate.of(2026, 9, 1));
        query.setToDate(LocalDate.of(2026, 9, 30));

        controller.activity(query);
        controller.instruments(query);
        controller.clients(query);
        controller.trends(query);

        verify(activityService).find(query.toFilters());
        verify(instrumentService).calculate(query.toFilters());
        verify(clientService).calculate(query.toFilters());
        verify(trendService).calculate(query.toFilters());
    }
}
