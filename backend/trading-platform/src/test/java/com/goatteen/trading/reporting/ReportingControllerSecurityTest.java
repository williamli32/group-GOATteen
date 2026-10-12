
package com.goatteen.trading.reporting;

import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.user.UserRepository;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.reporting.controller.ReportingController;
import com.goatteen.trading.reporting.metrics.ClientMetricsService;
import com.goatteen.trading.reporting.metrics.InstrumentMetricsService;
import com.goatteen.trading.reporting.metrics.PeriodicReportingService;
import com.goatteen.trading.reporting.metrics.TradingActivityMetricsService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReportingControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;
    @MockBean
    private TradingActivityMetricsService tradingService;

    @MockBean
    private InstrumentMetricsService instrumentService;

    @MockBean
    private ClientMetricsService clientService;

    @MockBean
    private PeriodicReportingService periodicService;

    private static final String TRADING_URL =
            "/api/reporting/trading"
                    + "?startDate=2026-10-01T00:00:00"
                    + "&endDate=2026-11-01T00:00:00";

    @Test
    void unauthenticatedUserGets401() throws Exception {
        mockMvc.perform(get(TRADING_URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tradingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserGets403() throws Exception {
        mockMvc.perform(get(TRADING_URL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tradingService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessReporting() throws Exception {
        mockMvc.perform(get(TRADING_URL))
                .andExpect(status().isOk());

        verify(tradingService).calculate(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0)
        );
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystCanAccessReporting() throws Exception {
        mockMvc.perform(get(TRADING_URL))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserCannotAccessClientReports() throws Exception {
        mockMvc.perform(get("/api/reporting/clients")
                .param("startDate", "2026-10-01T00:00:00")
                .param("endDate", "2026-11-01T00:00:00"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clientService);
    }
}
