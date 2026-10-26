package com.goatteen.trading.auth.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Bounded, per-instance limits. Share counters at the edge before scaling replicas. */
public final class AuthRateLimiter {
    private final AuthProperties.RateLimit settings;
    private final Clock clock;
    private final Map<String, Window> addresses = new HashMap<>();
    private final Map<String, Window> failures = new HashMap<>();

    public AuthRateLimiter(AuthProperties properties, Clock clock) {
        settings = properties.getRateLimit();
        this.clock = clock;
        if (settings.getRequestsPerMinute() < 1 || settings.getFailedLogins() < 1
                || settings.getFailureWindowSeconds() < 1 || settings.getMaxEntries() < 1
                || properties.getMaximumSessionDays() < 1) {
            throw new IllegalArgumentException("Authentication limits must be positive");
        }
    }

    public synchronized void checkAddress(String address) {
        if (!settings.isEnabled()) return;
        long now = clock.millis();
        Window window = window(addresses, address, now, 60000);
        requireAvailable(window, settings.getRequestsPerMinute(), now);
        window.count++;
    }

    public synchronized void checkLogin(String email) {
        if (!settings.isEnabled()) return;
        Window window = failures.get(normalize(email));
        if (window != null) requireAvailable(window, settings.getFailedLogins(), clock.millis());
    }

    public synchronized void failedLogin(String email) {
        if (!settings.isEnabled()) return;
        long now = clock.millis();
        window(failures, normalize(email), now, settings.getFailureWindowSeconds() * 1000L).count++;
    }

    public synchronized void successfulLogin(String email) {
        failures.remove(normalize(email));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Window window(Map<String, Window> windows, String key, long now, long duration) {
        Window current = windows.get(key);
        if (current != null && current.until > now) return current;
        windows.entrySet().removeIf(entry -> entry.getValue().until <= now);
        if (windows.size() >= settings.getMaxEntries()) throw new Limited(60);
        Window created = new Window(now + duration);
        windows.put(key, created);
        return created;
    }

    private void requireAvailable(Window window, int limit, long now) {
        if (window.until > now && window.count >= limit) {
            throw new Limited(Math.max(1, (window.until - now + 999) / 1000));
        }
    }

    private static final class Window {
        final long until;
        int count;
        Window(long until) { this.until = until; }
    }

    public static final class Limited extends RuntimeException {
        private final long retryAfter;
        public Limited(long retryAfter) {
            super("Too many authentication attempts. Try again shortly.");
            this.retryAfter = retryAfter;
        }
        public long retryAfter() { return retryAfter; }
    }
}
