package com.goatteen.trading.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository
        extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.refreshTokenHash = :hash")
    Optional<AuthSession> findForUpdate(@Param("hash") String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.refreshFamilyHash = :hash")
    Optional<AuthSession> findFamilyForUpdate(@Param("hash") String refreshFamilyHash);

    List<AuthSession> findByUserIdAndRevokedAtIsNull(Long userId);
}
