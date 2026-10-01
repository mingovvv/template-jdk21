package com.example.skeleton.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code Instant.now()} 직접 호출 대신 {@link Clock}을 주입받게 하여 테스트에서 시간을 고정할 수 있게 함. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
