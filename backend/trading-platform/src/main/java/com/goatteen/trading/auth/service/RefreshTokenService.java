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

        String family = generateToken();
        String rawToken = family + "." + generateToken();
        String tokenHash = hash(rawToken);

        LocalDateTime expiresAt =
                LocalDateTime.now().plusDays(
                        Math.min(authProperties.getRefreshTokenExpirationDays(), authProperties.getMaximumSessionDays())
                );

        AuthSession session = new AuthSession(
                user,
                tokenHash,
                expiresAt
        );

        session.setRefreshFamilyHash(hash(family));
        authSessionRepository.save(session);

        return new RefreshTokenResult(
                rawToken,
                expiresAt
        );
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RefreshTokenResult rotateSession(
            String rawToken
    ) {

        String currentHash = hash(rawToken);

        AuthSession session =
                authSessionRepository
                        .findForUpdate(currentHash)
                        .orElseThrow(
                                InvalidRefreshTokenException::new
                        );

        LocalDateTime absoluteExpiry = session.getCreatedAt().plusDays(authProperties.getMaximumSessionDays());
        if (!session.isActive() || !session.getUser().isEnabled()
                || !absoluteExpiry.isAfter(LocalDateTime.now())) {
            session.revoke();
            authSessionRepository.save(session);
            throw new InvalidRefreshTokenException();
        }

        String family = familyOf(rawToken);
        if (family == null || session.getRefreshFamilyHash() == null) {
            // Migrate an existing, opaque legacy token when it is first rotated.
            family = family == null ? rawToken : family;
            session.setRefreshFamilyHash(hash(family));
        }
        String newRawToken = family + "." + generateToken();
        String newHash = hash(newRawToken);

        LocalDateTime newExpiresAt =
                LocalDateTime.now().plusDays(
                        authProperties.getRefreshTokenExpirationDays()
                );

        if (newExpiresAt.isAfter(absoluteExpiry)) newExpiresAt = absoluteExpiry;

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

        String family = familyOf(rawToken);
        // A stale cookie may reach logout after a concurrent rotation committed.
        // The unpredictable family secret proves ownership without accepting the stale token for refresh.
        var found = family == null
                ? authSessionRepository.findForUpdate(hash).or(() -> authSessionRepository.findFamilyForUpdate(hash))
                : authSessionRepository.findFamilyForUpdate(hash(family));
        found
                .ifPresent(session -> {
                    session.revoke();
                    authSessionRepository.save(session);
                });
    }

    private String generateToken() {
        return UUID.randomUUID().toString()
                + UUID.randomUUID();
    }

    private String familyOf(String token) {
        int separator = token.indexOf('.');
        return separator > 0 && separator < token.length() - 1 && separator == token.lastIndexOf('.')
                ? token.substring(0, separator) : null;
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
