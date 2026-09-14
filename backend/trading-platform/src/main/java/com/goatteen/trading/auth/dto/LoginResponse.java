package com.goatteen.trading.auth.dto;

import java.util.Set;

public class LoginResponse {

    private final Long userId;
    private final String email;
    private final Set<String> roles;
    private final String accessToken;

    public LoginResponse(
            Long userId,
            String email,
            Set<String> roles,
            String accessToken
    ) {
        this.userId = userId;
        this.email = email;
        this.roles = roles;
        this.accessToken = accessToken;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public String getAccessToken() {
        return accessToken;
    }
}