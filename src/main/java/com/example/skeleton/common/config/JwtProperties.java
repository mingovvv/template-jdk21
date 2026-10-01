package com.example.skeleton.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 서명 설정.
 * 토큰을 HMAC-SHA256으로 검증하므로 시크릿은 최소 32바이트여야 함.
 */
@ConfigurationProperties(prefix = "skeleton.security.jwt")
public record JwtProperties(

    String secret

) {

    private static final int MIN_SECRET_LENGTH = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("skeleton.security.jwt.secret must be at least %d bytes".formatted(MIN_SECRET_LENGTH));
        }
    }
}
