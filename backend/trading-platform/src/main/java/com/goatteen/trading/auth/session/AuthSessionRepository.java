package com.goatteen.trading.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository
        extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    List<AuthSession> findByUserIdAndRevokedAtIsNull(Long userId);
}