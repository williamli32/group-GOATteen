package com.goatteen.trading.auth;

import com.goatteen.trading.auth.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {
    private static class Time extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    @Test void accountFailuresAreNormalizedAndExpireWithoutPermanentlyLockingAUser() {
        var clock = new Time(); var p = new AuthProperties();
        p.getRateLimit().setFailedLogins(2);
        var limiter = new AuthRateLimiter(p, clock);
        limiter.failedLogin(" User@Example.Test "); limiter.failedLogin("user@example.test");
        assertThrows(AuthRateLimiter.Limited.class, () -> limiter.checkLogin("USER@example.test"));
        clock.now = clock.now.plusSeconds(301);
        assertDoesNotThrow(() -> limiter.checkLogin("user@example.test"));
        limiter.failedLogin("user@example.test");
        limiter.successfulLogin("user@example.test");
        assertDoesNotThrow(() -> limiter.checkLogin("user@example.test"));
    }

    @Test void signupAndRefreshShareAnAddressBudgetAndReturnRetryAfter() throws Exception {
        var p = new AuthProperties(); p.getRateLimit().setRequestsPerMinute(2);
        var filter = new AuthRateLimitFilter(new AuthRateLimiter(p, new Time()));
        var passed = new AtomicInteger();
        for (String path : new String[]{"/api/auth/register", "/api/auth/refresh", "/api/auth/login"}) {
            var request = new MockHttpServletRequest("POST", path);
            request.addHeader("X-Forwarded-For", "untrusted-changing-value-" + path);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (a, b) -> passed.incrementAndGet());
            if (path.endsWith("login")) {
                assertEquals(429, response.getStatus());
                assertEquals("60", response.getHeader("Retry-After"));
            }
        }
        assertEquals(2, passed.get());
    }

    @Test void identityChurnCannotGrowTheCountersWithoutBound() {
        var p = new AuthProperties(); p.getRateLimit().setMaxEntries(2);
        var time = new Time(); var limiter = new AuthRateLimiter(p, time);
        limiter.checkAddress("one"); limiter.checkAddress("two");
        assertThrows(AuthRateLimiter.Limited.class, () -> limiter.checkAddress("three"));
        time.now = time.now.plusSeconds(61);
        assertDoesNotThrow(() -> limiter.checkAddress("three"));
    }
}
