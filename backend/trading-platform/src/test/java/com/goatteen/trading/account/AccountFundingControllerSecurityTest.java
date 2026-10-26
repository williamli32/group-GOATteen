package com.goatteen.trading.account;

import com.goatteen.trading.account.controller.AccountFundingController;
import com.goatteen.trading.account.dto.AddFundsRequest;
import com.goatteen.trading.account.dto.AddFundsResponse;
import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.auth.service.CurrentUserService;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountFundingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AccountFundingControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean AccountFundingService funding;
    @MockitoBean CurrentUserService currentUser;

    private String body(String amount) {
        return "{\"amount\":\"" + amount + "\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}";
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/accounts/me/funds").contentType(MediaType.APPLICATION_JSON).content(body("10000")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(funding);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void derivesTheAccountOwnerFromAuthentication() throws Exception {
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(funding.addFunds(eq(7L), any(AddFundsRequest.class))).thenReturn(new AddFundsResponse(
                10L, 20L, new BigDecimal("10000"), "USD", new BigDecimal("10000"), LocalDateTime.now(), false));
        String body = body("10000").replace("}", ",\"accountId\":999,\"currency\":\"GBP\"}");
        mvc.perform(post("/api/accounts/me/funds").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.accountId").value(10));
        verify(funding).addFunds(eq(7L), any(AddFundsRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000000000"})
    @WithMockUser(roles = "CLIENT")
    void validatesAmountsBeforeCallingFunding(String amount) throws Exception {
        mvc.perform(post("/api/accounts/me/funds").contentType(MediaType.APPLICATION_JSON).content(body(amount)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(funding);
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void requiresAFundingRequestId() throws Exception {
        mvc.perform(post("/api/accounts/me/funds").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"10\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(funding);
    }
}
