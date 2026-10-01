package com.example.skeleton.ping;

import java.time.Clock;
import java.time.Instant;
import com.example.skeleton.common.context.MdcKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 스모크 테스트 엔드포인트.
 * 토큰 없이 curl 한 번으로 서버 기동 여부를 확인하고, 요청 ID를 에코해 로깅 파이프라인 동작까지 확인할 수 있음.
 */
@RestController
@RequestMapping("/api/ping")
class PingController {

    private final Clock clock;

    PingController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    PingResponse ping() {
        return PingResponse.builder()
                .message("pong")
                .serverTime(Instant.now(clock))
                .requestId(MdcKey.REQUEST_ID.get())
                .build();
    }
}
