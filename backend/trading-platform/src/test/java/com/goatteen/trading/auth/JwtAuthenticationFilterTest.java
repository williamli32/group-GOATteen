package com.goatteen.trading.auth;

import com.goatteen.trading.auth.security.*;
import com.goatteen.trading.user.*;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void disabledUserCannotAuthenticateWithAnOtherwiseValidJwt() throws Exception {
        var jwt = mock(JwtService.class); var users = mock(UserRepository.class);
        var user = mock(User.class);
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(jwt.extractUserId("token")).thenReturn(1L);
        when(users.findWithRolesById(1L)).thenReturn(Optional.of(user));
        when(user.isEnabled()).thenReturn(false);
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer token");
        new JwtAuthenticationFilter(jwt, users).doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(user, never()).getRoles();
    }

    @Test void expiryBetweenValidationAndExtractionDoesNotBecomeAServerError() throws Exception {
        var jwt = mock(JwtService.class); var users = mock(UserRepository.class);
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(jwt.extractUserId("token")).thenThrow(new IllegalArgumentException("expired"));
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer token");
        var chain = mock(FilterChain.class); var response = new MockHttpServletResponse();
        new JwtAuthenticationFilter(jwt, users).doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
    }
}
