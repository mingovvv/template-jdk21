package com.example.skeleton.ping;

import java.time.Instant;
import lombok.Builder;

@Builder
public record PingResponse(
        String message,
        Instant serverTime,
        String requestId) {
}
