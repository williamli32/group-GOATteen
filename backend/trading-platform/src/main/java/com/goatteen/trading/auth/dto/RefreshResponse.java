package com.goatteen.trading.auth.dto;

public class RefreshResponse {

    private final String accessToken;

    public RefreshResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }
}