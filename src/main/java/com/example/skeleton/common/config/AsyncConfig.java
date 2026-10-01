package com.example.skeleton.common.config;

import com.example.skeleton.common.context.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 실행 기반.
 * {@link TaskDecorator} 빈을 등록해두면 Spring Boot가 자동 구성하는 기본 executor(applicationTaskExecutor)에 자동으로 적용됨.
 * 그래서 비동기 작업도 requestId(MDC)를 승계함.
 *
 * 커스텀 executor를 직접 만들 때도 반드시 {@code setTaskDecorator(new MdcTaskDecorator())}를 적용해야 함.
 * 빼먹으면 그 executor를 타는 로그에서 requestId가 조용히 사라짐.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
