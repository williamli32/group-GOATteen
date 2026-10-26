package com.goatteen.trading.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public final class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter;
    public AuthRateLimitFilter(AuthRateLimiter limiter) { this.limiter = limiter; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (request.getMethod().equals("POST") && request.getRequestURI()
                .substring(request.getContextPath().length()).startsWith("/api/auth/")) {
            try {
                // In deployment only Caddy reaches this API; it supplies trusted forwarding headers.
                limiter.checkAddress(request.getRemoteAddr());
            } catch (AuthRateLimiter.Limited limited) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(limited.retryAfter()));
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                        + "\"message\":\"Too many authentication attempts. Try again shortly.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
