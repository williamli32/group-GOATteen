package com.goatteen.trading.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private long refreshTokenExpirationDays;

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