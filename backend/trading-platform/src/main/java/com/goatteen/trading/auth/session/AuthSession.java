package com.goatteen.trading.auth.session;

import com.goatteen.trading.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    protected AuthSession() {
        // Required by JPA
    }

    public AuthSession(
            User user,
            String refreshTokenHash,
            LocalDateTime expiresAt) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return !expiresAt.isAfter(LocalDateTime.now());
    }

    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now();
        }
    }

    public void markUsed() {
        lastUsedAt = LocalDateTime.now();
    }

    public void rotate(
            String newRefreshTokenHash,
            LocalDateTime newExpiresAt) {
        this.refreshTokenHash = newRefreshTokenHash;
        this.expiresAt = newExpiresAt;
        this.lastUsedAt = LocalDateTime.now();
    }
}