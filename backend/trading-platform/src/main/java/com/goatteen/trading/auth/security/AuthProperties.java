package com.goatteen.trading.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private long refreshTokenExpirationDays;
    private String internalAccessCode;
    private long maximumSessionDays = 30;
    private final RateLimit rateLimit = new RateLimit();

    public long getMaximumSessionDays() { return maximumSessionDays; }
    public void setMaximumSessionDays(long value) { maximumSessionDays = value; }
    public RateLimit getRateLimit() { return rateLimit; }

    public static class RateLimit {
        private boolean enabled = true;
        private int requestsPerMinute = 60;
        private int failedLogins = 10;
        private int failureWindowSeconds = 300;
        private int maxEntries = 10000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int value) { requestsPerMinute = value; }
        public int getFailedLogins() { return failedLogins; }
        public void setFailedLogins(int value) { failedLogins = value; }
        public int getFailureWindowSeconds() { return failureWindowSeconds; }
        public void setFailureWindowSeconds(int value) { failureWindowSeconds = value; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int value) { maxEntries = value; }
    }

    public String getInternalAccessCode() {
        return internalAccessCode;
    }

    public void setInternalAccessCode(String internalAccessCode) {
        this.internalAccessCode = internalAccessCode;
    }

    public long getRefreshTokenExpirationDays() {
        return refreshTokenExpirationDays;
    }

    public void setRefreshTokenExpirationDays(
            long refreshTokenExpirationDays
    ) {
        this.refreshTokenExpirationDays =
                refreshTokenExpirationDays;
    }
}
