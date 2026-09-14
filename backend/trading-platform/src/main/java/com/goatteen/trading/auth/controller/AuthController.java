package com.goatteen.trading.auth.controller;

import com.goatteen.trading.auth.dto.RegisterRequest;
import com.goatteen.trading.auth.dto.RegisterResponse;
import com.goatteen.trading.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.goatteen.trading.auth.dto.LoginRequest;
import com.goatteen.trading.auth.dto.LoginResponse;
import com.goatteen.trading.auth.dto.RefreshResponse;
import com.goatteen.trading.auth.service.RefreshTokenService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private static final String REFRESH_COOKIE = "leap_refresh_token";

    private final RefreshTokenService refreshTokenService;

    public AuthController(
            AuthService authService,
            RefreshTokenService refreshTokenService) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        RegisterResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse) {

        AuthService.LoginResult result = authService.login(request);

        addRefreshCookie(
                servletResponse,
                result.refreshToken(),
                Duration.between(
                        java.time.LocalDateTime.now(),
                        result.refreshTokenExpiresAt()).toSeconds());

        return ResponseEntity.ok(
                result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = getRefreshTokenFromCookie(request);

        if (refreshToken == null) {
            throw new RefreshTokenService.InvalidRefreshTokenException();
        }

        RefreshTokenService.RefreshTokenResult result = refreshTokenService.rotateSession(
                refreshToken);

        String accessToken = authService.generateAccessToken(
                result.user());

        addRefreshCookie(
                response,
                result.token(),
                Duration.between(
                        java.time.LocalDateTime.now(),
                        result.expiresAt()).toSeconds());

        return ResponseEntity.ok(
                new RefreshResponse(accessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = getRefreshTokenFromCookie(request);

        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }

        clearRefreshCookie(response);

        return ResponseEntity.noContent().build();
    }

    private void addRefreshCookie(
            HttpServletResponse response,
            String token,
            long maxAgeSeconds) {

        Cookie cookie = new Cookie(
                REFRESH_COOKIE,
                token);

        cookie.setHttpOnly(true);
        cookie.setSecure(false); // local HTTP development only
        cookie.setPath("/api/auth");
        cookie.setMaxAge(
                (int) Math.max(maxAgeSeconds, 0));

        response.addCookie(cookie);
    }

    private String getRefreshTokenFromCookie(
            HttpServletRequest request) {

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {

            if (REFRESH_COOKIE.equals(
                    cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private void clearRefreshCookie(
            HttpServletResponse response) {

        Cookie cookie = new Cookie(
                REFRESH_COOKIE,
                "");

        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);

        response.addCookie(cookie);
    }
}