package com.goatteen.trading.auth;

import com.goatteen.trading.auth.controller.AuthController;
import com.goatteen.trading.auth.security.JwtAuthenticationFilter;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.auth.service.AuthService;
import com.goatteen.trading.auth.service.RefreshTokenService;
import com.goatteen.trading.config.SecurityConfig;
import com.goatteen.trading.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthRegistrationControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JwtService jwt;
    @MockBean UserRepository users;
    @MockBean AuthService auth;
    @MockBean RefreshTokenService refresh;

    @Test
    void currenciesCanBeLoadedBeforeSigningIn() throws Exception {
        mvc.perform(get("/api/auth/account-currencies")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'USD')].minorUnits").value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.code == 'JPY')].minorUnits").value(org.hamcrest.Matchers.contains(0)));
    }

    @Test
    void registrationRequiresCurrency() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Test","lastName":"User","email":"test@example.com",
                 "password":"Password123!","role":"CLIENT"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.currency").exists());
        verifyNoInteractions(auth);
    }

    @Test
    void administratorIsNotASignupOption() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Test","lastName":"User","email":"test@example.com",
                 "password":"Password123!","role":"ADMIN","currency":"USD"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.role").exists());
        verifyNoInteractions(auth);
    }
}
