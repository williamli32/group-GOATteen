package com.goatteen.trading.auth;

import com.goatteen.trading.auth.controller.AuthController;
import com.goatteen.trading.auth.dto.LoginResponse;
import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.auth.service.AuthService;
import com.goatteen.trading.auth.service.RefreshTokenService;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.user.User;
import com.goatteen.trading.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.time.LocalDateTime;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class, properties = {
        "app.cors.allowed-origins=https://trading.example.com"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthTransportTest {
    private static final String COOKIE = "leap_refresh_token";
    private static final String LOGIN = """
            {"email":"customer@example.com","password":"Password123!"}
            """;

    @Autowired MockMvc mvc;
    @Autowired com.goatteen.trading.auth.security.AuthRateLimiter limiter;
    @MockBean JwtService jwt;
    @MockBean UserRepository users;
    @MockBean AuthService auth;
    @MockBean RefreshTokenService refresh;

    private void stubLogin() {
        var response = new LoginResponse(1L, "customer@example.com", Set.of("CLIENT"), "access-token");
        when(auth.login(any())).thenReturn(new AuthService.LoginResult(
                response, "refresh-token", LocalDateTime.now().plusDays(7)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginCookieFollowsTheRequestTransport(boolean secure) throws Exception {
        stubLogin();
        mvc.perform(post("/api/auth/login").with(csrf()).secure(secure)
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(cookie().value(COOKIE, "refresh-token"))
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().secure(COOKIE, secure))
                .andExpect(cookie().path(COOKIE, "/api/auth"))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rotatedCookieKeepsTheRequestTransport(boolean secure) throws Exception {
        User user = mock(User.class);
        when(refresh.rotateSession("previous-token")).thenReturn(
                new RefreshTokenService.RefreshTokenResult("rotated-token", LocalDateTime.now().plusDays(7), user));
        when(auth.generateAccessToken(user)).thenReturn("access-token");
        mvc.perform(post("/api/auth/refresh").with(csrf()).secure(secure)
                        .cookie(new Cookie(COOKIE, "previous-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().value(COOKIE, "rotated-token"))
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().secure(COOKIE, secure))
                .andExpect(cookie().path(COOKIE, "/api/auth"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void logoutClearsTheCookieWithMatchingTransportAndPath(boolean secure) throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()).secure(secure)
                        .cookie(new Cookie(COOKIE, "refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0))
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().secure(COOKIE, secure))
                .andExpect(cookie().path(COOKIE, "/api/auth"));
        verify(refresh).revoke("refresh-token");
    }

    @Test
    void httpsTerminatedAtTheProxyStillProducesASecureCookie() throws Exception {
        stubLogin();
        MockMvc behindProxy = MockMvcBuilders.standaloneSetup(new AuthController(auth, refresh, limiter))
                .addFilters(new ForwardedHeaderFilter()).build();
        behindProxy.perform(post("/api/auth/login").with(csrf())
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "trading.example.com")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(cookie().secure(COOKIE, true));
    }

    @Test
    void theConfiguredCloudOriginCanPreflightAnAuthenticatedApi() throws Exception {
        mvc.perform(options("/api/accounts/me")
                        .header("Origin", "https://trading.example.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://trading.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void anUnconfiguredOriginCannotPreflightTheApi() throws Exception {
        mvc.perform(options("/api/accounts/me")
                        .header("Origin", "https://untrusted.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
