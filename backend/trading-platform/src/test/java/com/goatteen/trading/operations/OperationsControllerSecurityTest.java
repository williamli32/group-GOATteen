package com.goatteen.trading.operations;

import com.goatteen.trading.audit.TradeReconstructionService;
import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class OperationsControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean OperationsOrderSearchService search;
    @MockitoBean TradeReconstructionService reconstruction;

    @Test
    void anonymousCannotSearch() throws Exception {
        mvc.perform(get("/api/internal/operations/orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(search);
    }

    @Test @WithMockUser(roles = "OPERATIONS")
    void operationsCanSearch() throws Exception {
        mvc.perform(get("/api/internal/operations/orders")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void administratorsRetainAccess() throws Exception {
        mvc.perform(get("/api/internal/operations/orders")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "CLIENT")
    void customerCannotSearch() throws Exception {
        mvc.perform(get("/api/internal/operations/orders")).andExpect(status().isForbidden());
        verifyNoInteractions(search);
    }

    @Test @WithMockUser(roles = "ANALYST")
    void analystCannotSearch() throws Exception {
        mvc.perform(get("/api/internal/operations/orders")).andExpect(status().isForbidden());
        verifyNoInteractions(search);
    }
}
