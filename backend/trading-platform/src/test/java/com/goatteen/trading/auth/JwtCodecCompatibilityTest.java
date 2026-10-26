package com.goatteen.trading.auth;

import com.goatteen.trading.auth.security.JwtProperties;
import com.goatteen.trading.auth.security.JwtService;
import com.goatteen.trading.role.Role;
import com.goatteen.trading.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtCodecCompatibilityTest {
    private JwtService service(byte key, long minutes) {
        byte[] secret = new byte[64];
        java.util.Arrays.fill(secret, key);
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(secret));
        properties.setAccessTokenExpirationMinutes(minutes);
        return new JwtService(properties);
    }

    @Test void realJwtCodecKeepsClaimsAndEnforcesSignatureAndExpiry() {
        User user = new User("codec@example.test", "unused-test-hash");
        Role role = new Role();
        ReflectionTestUtils.setField(user, "id", 17L);
        ReflectionTestUtils.setField(role, "name", "OPERATIONS");
        user.addRole(role);
        JwtService issuer = service((byte) 1, 15);
        String token = issuer.generateAccessToken(user);
        var claims = issuer.parseToken(token);

        assertEquals(17L, issuer.extractUserId(token));
        assertEquals(user.getEmail(), issuer.extractEmail(token));
        assertEquals(List.of("OPERATIONS"), claims.get("roles", List.class));
        assertEquals(Duration.ofMinutes(15), Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()));
        assertFalse(service((byte) 2, 15).isTokenValid(token));
        assertFalse(issuer.isTokenValid(service((byte) 1, -1).generateAccessToken(user)));
    }
}
