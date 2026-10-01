package com.example.skeleton.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;

/**
 * Boot 배선 검증. {@code @EnableAsync}가 켜져 있고, 자동 구성된 기본 executor에 MdcTaskDecorator가 실제로 적용되어 비동기 작업이 requestId를 승계하는지 확인함.
 * 단위 테스트(MdcTaskDecoratorTest)는 데코레이터 로직만 검증하므로, 빈 등록이 누락되면 이 테스트만이 잡아냄.
 */
@SpringBootTest
class AsyncMdcPropagationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    static class AsyncProbe {

        record Observation(String threadName, String requestId) {
        }

        @Async
        public CompletableFuture<Observation> observe() {
            return CompletableFuture.completedFuture(
                    new Observation(Thread.currentThread().getName(), MdcKey.REQUEST_ID.get()));
        }
    }

    @Autowired
    private AsyncProbe probe;

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void asyncTasksInheritTheRequestId() throws Exception {
        MdcKey.REQUEST_ID.put("async-test-id");

        AsyncProbe.Observation observation = probe.observe().get(5, TimeUnit.SECONDS);

        assertThat(observation.threadName())
                .as("must actually run on the async executor — otherwise MDC inheritance is trivial")
                .isNotEqualTo(Thread.currentThread().getName());
        assertThat(observation.requestId()).isEqualTo("async-test-id");
    }
}
