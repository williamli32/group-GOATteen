package com.goatteen.trading.reporting.tests;

import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.reporting.controller.InternalReportingController;
import com.goatteen.trading.reporting.metrics.ActivityTrendService;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;
import com.goatteen.trading.reporting.metrics.ReportingActivityService;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalReportingController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class })
class InternalReportingControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TradingActivityMetricsService tradingActivityService;

    @MockBean
    private ReportingActivityService activityService;

    @MockBean
    private InstrumentMetricsService instrumentService;

    @MockBean
    private ClientMetricsService clientService;

    @MockBean
    private ActivityTrendService trendService;

    @Test
    void unauthenticatedUserGets401() throws Exception {
        mockMvc.perform(get("/api/internal/reporting/summary")
                .param("fromDate", "2026-09-01")
                .param("toDate", "2026-09-30"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tradingActivityService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserGets403() throws Exception {
        mockMvc.perform(get("/api/internal/reporting/instruments")
                .param("fromDate", "2026-09-01")
                .param("toDate", "2026-09-30"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(instrumentService);
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCanAccessInternalReporting() throws Exception {
        mockMvc.perform(get("/api/internal/reporting/trends")
                .param("fromDate", "2026-09-01")
                .param("toDate", "2026-09-30"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "activity", "instruments", "clients", "trends"})
    @WithMockUser(roles = "OPERATIONS")
    void operationsCanAccessEveryInternalReport(String endpoint) throws Exception {
        mockMvc.perform(get("/api/internal/reporting/" + endpoint)
                .param("fromDate", "2026-09-01")
                .param("toDate", "2026-09-30"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "activity", "instruments", "clients", "trends"})
    @WithMockUser(roles = "CLIENT")
    void customersCannotAccessInternalReports(String endpoint) throws Exception {
        mockMvc.perform(get("/api/internal/reporting/" + endpoint)
                .param("fromDate", "2026-09-01")
                .param("toDate", "2026-09-30"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tradingActivityService, activityService, instrumentService, clientService, trendService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessInternalReporting() throws Exception {

        mockMvc.perform(
                get("/api/internal/reporting/summary")
                        .param(
                                "fromDate",
                                "2026-09-01")
                        .param(
                                "toDate",
                                "2026-09-30"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void invalidReportingFiltersReturn400() throws Exception {

        mockMvc.perform(
                get("/api/internal/reporting/summary")
                        .param(
                                "fromDate",
                                "2026-09-01")
                        .param(
                                "toDate",
                                "2026-09-30")
                        .param(
                                "side",
                                "INVALID"))
                .andExpect(status().isBadRequest());
    }
}
