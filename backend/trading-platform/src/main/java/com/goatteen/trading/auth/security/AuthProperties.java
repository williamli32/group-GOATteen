package com.goatteen.trading.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private long refreshTokenExpirationDays;
    private String internalAccessCode;

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