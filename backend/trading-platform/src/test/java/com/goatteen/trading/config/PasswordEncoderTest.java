package com.goatteen.trading.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder(12);

    @Test
    void shouldHashPasswordAndMatchOriginalPassword() {

        String rawPassword = "SecurePassword123!";

        String hash = passwordEncoder.encode(rawPassword);

        assertThat(hash).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, hash)).isTrue();
    }

    @Test
    void shouldRejectIncorrectPassword() {

        String hash =
                passwordEncoder.encode("CorrectPassword123!");

        assertThat(
                passwordEncoder.matches(
                        "WrongPassword123!",
                        hash
                )
        ).isFalse();
    }
}