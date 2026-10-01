package com.example.skeleton.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void acceptsSecretOfAtLeast32Bytes() {
        assertThatCode(() -> new JwtProperties("0123456789012345678901234567890123456789"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
