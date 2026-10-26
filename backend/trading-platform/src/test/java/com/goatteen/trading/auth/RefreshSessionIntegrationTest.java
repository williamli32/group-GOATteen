package com.goatteen.trading.auth;

import com.goatteen.trading.auth.service.RefreshTokenService;
import com.goatteen.trading.support.DatabaseIntegrationTestSupport;
import com.goatteen.trading.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RefreshSessionIntegrationTest extends DatabaseIntegrationTestSupport {
    @Autowired RefreshTokenService sessions;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private User user() {
        User user = users.save(new User(UUID.randomUUID() + "@session.test", "test-hash"));
        fixtures.user(user.getId());
        return user;
    }

    @Test void simultaneousRefreshesHaveOneWinnerAndTheOldTokenCannotBeReplayed() throws Exception {
        var user = user(); String old = sessions.createSession(user).token();
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<String> task = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try { return sessions.rotateSession(old).token(); }
                catch (RefreshTokenService.InvalidRefreshTokenException rejected) { return null; }
            };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            var tokens = Arrays.asList(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, tokens.stream().filter(Objects::nonNull).count());
            assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> sessions.rotateSession(old));
            String winner = tokens.stream().filter(Objects::nonNull).findFirst().orElseThrow();
            String next = sessions.rotateSession(winner).token();
            assertNotEquals(winner, next);
            sessions.revoke(next);
            assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> sessions.rotateSession(next));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=?", Integer.class, user.getId()));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void rejectingADisabledAccountAlsoPersistsItsSessionRevocation() {
        var user = user(); var token = sessions.createSession(user).token();
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?", user.getId());
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> sessions.rotateSession(token));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NOT NULL", Integer.class, user.getId()));
        jdbc.update("UPDATE users SET enabled=TRUE WHERE id=?", user.getId());
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> sessions.rotateSession(token));
    }

    @Test void logoutWithThePreviousCookieRevokesAConcurrentRefreshWinner() {
        var user = user(); var previous = sessions.createSession(user).token();
        var rotated = sessions.rotateSession(previous).token();
        sessions.revoke(previous);
        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> sessions.rotateSession(rotated));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NOT NULL", Integer.class, user.getId()));
    }
}
