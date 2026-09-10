package com.goatteen.trading.auth.service;

import com.goatteen.trading.auth.security.AuthProperties;
import com.goatteen.trading.auth.session.AuthSession;
import com.goatteen.trading.auth.session.AuthSessionRepository;
import com.goatteen.trading.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final AuthSessionRepository authSessionRepository;
    private final AuthProperties authProperties;

    public RefreshTokenService(
            AuthSessionRepository authSessionRepository,
            AuthProperties authProperties
    ) {
        this.authSessionRepository = authSessionRepository;
        this.authProperties = authProperties;
    }

    @Transactional
    public RefreshTokenResult createSession(User user) {

        String rawToken = generateToken();
        String tokenHash = hash(rawToken);

        LocalDateTime expiresAt =
                LocalDateTime.now().plusDays(
                        authProperties.getRefreshTokenExpirationDays()
                );

        AuthSession session = new AuthSession(
                user,
                tokenHash,
                expiresAt
        );

        authSessionRepository.save(session);

        return new RefreshTokenResult(
                rawToken,
                expiresAt
        );
    }

    @Transactional
    public RefreshTokenResult rotateSession(
            String rawToken
    ) {

        String currentHash = hash(rawToken);

        AuthSession session =
                authSessionRepository
                        .findByRefreshTokenHash(currentHash)
                        .orElseThrow(
                                InvalidRefreshTokenException::new
                        );

        if (!session.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        String newRawToken = generateToken();
        String newHash = hash(newRawToken);

        LocalDateTime newExpiresAt =
                LocalDateTime.now().plusDays(
                        authProperties.getRefreshTokenExpirationDays()
                );

        session.rotate(
                newHash,
                newExpiresAt
        );

        authSessionRepository.save(session);

        return new RefreshTokenResult(
                newRawToken,
                newExpiresAt,
                session.getUser()
        );
    }

    @Transactional
    public void revoke(String rawToken) {

        String hash = hash(rawToken);

        authSessionRepository
                .findByRefreshTokenHash(hash)
                .ifPresent(session -> {
                    session.revoke();
                    authSessionRepository.save(session);
                });
    }

    private String generateToken() {
        return UUID.randomUUID().toString()
                + UUID.randomUUID();
    }

    private String hash(String rawToken) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashed =
                    digest.digest(
                            rawToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return Base64.getEncoder()
                    .encodeToString(hashed);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    public record RefreshTokenResult(
            String token,
            LocalDateTime expiresAt,
            User user
    ) {

        public RefreshTokenResult(
                String token,
                LocalDateTime expiresAt
        ) {
            this(token, expiresAt, null);
        }
    }

    public static class InvalidRefreshTokenException
            extends RuntimeException {

        public InvalidRefreshTokenException() {
            super("Invalid or expired session");
        }
    }
}