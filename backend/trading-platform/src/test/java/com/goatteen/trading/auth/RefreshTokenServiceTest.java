package com.goatteen.trading.auth;

import com.goatteen.trading.auth.security.AuthProperties;
import com.goatteen.trading.auth.service.RefreshTokenService;
import com.goatteen.trading.auth.session.*;
import com.goatteen.trading.user.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private AuthSessionRepository repository;
    private RefreshTokenService service;
    private User user;
    private AuthSession session;

    @BeforeEach void setup() {
        repository = mock(AuthSessionRepository.class); user = mock(User.class);
        when(user.isEnabled()).thenReturn(true);
        var properties = new AuthProperties(); properties.setRefreshTokenExpirationDays(7);
        service = new RefreshTokenService(repository, properties);
        session = new AuthSession(user, "previous-hash", LocalDateTime.now().plusDays(7));
        when(repository.findForUpdate(anyString())).thenReturn(Optional.of(session));
    }

    @ParameterizedTest @ValueSource(strings = {"disabled", "expired", "revoked", "absolute-expiry"})
    void refusesAndRevokesAnUnusableSession(String state) {
        switch (state) {
            case "disabled" -> when(user.isEnabled()).thenReturn(false);
            case "expired" -> ReflectionTestUtils.setField(session, "expiresAt", LocalDateTime.now().minusSeconds(1));
            case "revoked" -> session.revoke();
            case "absolute-expiry" -> ReflectionTestUtils.setField(session, "createdAt", LocalDateTime.now().minusDays(31));
        }
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotateSession("old"));
        assertTrue(session.isRevoked());
        assertEquals("previous-hash", session.getRefreshTokenHash());
        verify(repository).save(session);
    }

    @Test void rotationUsesALockedLookupAndCannotExtendTheAbsoluteDeadline() {
        var created = LocalDateTime.now().minusDays(29);
        ReflectionTestUtils.setField(session, "createdAt", created);
        var result = service.rotateSession("old");
        assertEquals(created.plusDays(30), result.expiresAt());
        assertEquals(user, result.user());
        assertNotEquals("previous-hash", session.getRefreshTokenHash());
        assertNotEquals(result.token(), session.getRefreshTokenHash());
        verify(repository).findForUpdate(anyString());
        verify(repository, never()).findByRefreshTokenHash(anyString());
    }

    @Test void logoutLocksAndRevokesTheSession() {
        service.revoke("old");
        assertTrue(session.isRevoked());
        verify(repository).findForUpdate(anyString());
        verify(repository).save(session);
    }

    @Test void logoutCanRevokeTheFamilyOfAnAlreadyRetiredCookie() {
        when(repository.findFamilyForUpdate(anyString())).thenReturn(Optional.of(session));
        service.revoke("family-secret.retired-generation");
        assertTrue(session.isRevoked());
        verify(repository).findFamilyForUpdate(anyString());
        verify(repository, never()).findByRefreshTokenHash(anyString());
    }

    @Test void legacyTokenBecomesTheFamilyProofSoRacingLegacyLogoutStillWorks() {
        var rotated = service.rotateSession("legacy-secret");
        assertTrue(rotated.token().startsWith("legacy-secret."));
        assertNotNull(session.getRefreshFamilyHash());
        when(repository.findForUpdate(anyString())).thenReturn(Optional.empty());
        when(repository.findFamilyForUpdate(anyString())).thenReturn(Optional.of(session));
        service.revoke("legacy-secret");
        assertTrue(session.isRevoked());
    }
}
