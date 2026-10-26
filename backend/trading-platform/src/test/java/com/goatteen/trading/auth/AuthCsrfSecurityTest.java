package com.goatteen.trading.auth;

import com.goatteen.trading.auth.controller.AuthController;
import com.goatteen.trading.auth.dto.LoginResponse;
import com.goatteen.trading.auth.security.*;
import com.goatteen.trading.auth.service.*;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.user.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.LocalDateTime;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class, properties = "app.auth.rate-limit.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthCsrfSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean AuthService auth;
    @MockitoBean RefreshTokenService refresh;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;

    @ParameterizedTest @ValueSource(strings = {"login", "register", "refresh", "logout"})
    void cookieAndCredentialEndpointsRejectMissingCsrf(String path) throws Exception {
        mvc.perform(post("/api/auth/" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth, refresh);
    }

    @Test void maskedTokenAndHttpOnlyCookieAuthorizeLoginAndTamperingIsRejected() throws Exception {
        var boot = mvc.perform(get("/api/auth/csrf").secure(true))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("XSRF-TOKEN", true))
                .andExpect(cookie().secure("XSRF-TOKEN", true))
                .andReturn();
        var csrf = json.readTree(boot.getResponse().getContentAsString());
        var cookie = boot.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        assertNotEquals(cookie.getValue(), csrf.get("token").asText());
        when(auth.login(any())).thenReturn(new AuthService.LoginResult(
                new LoginResponse(1L, "user@example.test", Set.of("CLIENT"), "jwt"),
                "refresh", LocalDateTime.now().plusDays(7)));
        String body = "{\"email\":\"user@example.test\",\"password\":\"Password123!\"}";
        mvc.perform(post("/api/auth/login").secure(true).cookie(cookie)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").secure(true).cookie(cookie)
                .header(csrf.get("headerName").asText(), "tampered")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verify(auth, times(1)).login(any());
    }
}
