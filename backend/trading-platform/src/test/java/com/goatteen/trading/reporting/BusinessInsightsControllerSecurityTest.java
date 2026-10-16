package com.goatteen.trading.reporting;

import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.reporting.controller.BusinessInsightsController;
import com.goatteen.trading.reporting.metrics.BusinessInsightsService;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BusinessInsightsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class BusinessInsightsControllerSecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository userRepository;
    @MockBean private BusinessInsightsService insights;

    private MockHttpServletRequestBuilder request() {
        return get("/api/internal/reporting/insights")
                .param("fromDate", "2026-10-01").param("toDate", "2026-10-16").param("currency", "USD");
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(request()).andExpect(status().isUnauthorized());
        verifyNoInteractions(insights);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void clientsCannotReadCrossClientInsights() throws Exception {
        mvc.perform(request()).andExpect(status().isForbidden());
        verifyNoInteractions(insights);
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void analystsCanReadInsights() throws Exception {
        mvc.perform(request()).andExpect(status().isOk());
        verify(insights).calculate(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorsCanReadInsights() throws Exception {
        mvc.perform(request()).andExpect(status().isOk());
    }
}
