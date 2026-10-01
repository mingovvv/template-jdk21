package com.example.skeleton.common.context;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * MDC는 ThreadLocal 기반이라 {@code @Async} 등으로 다른 스레드에 넘어간 작업은 requestId를 잃음.
 * 이 데코레이터는 작업 제출 시점의 MDC 스냅샷을 실행 스레드로 복사해 비동기 로그에도 같은 requestId가 붙게 함.
 *
 * 실행 후 {@code MDC.clear()}가 필수임.
 * 풀 스레드는 재사용되므로 지우지 않으면 이전 작업의 requestId가 다음 작업 로그에 남음.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> parentContext = MDC.getCopyOfContextMap();
        return () -> {
            if (parentContext != null) {
                MDC.setContextMap(parentContext);
            } else {
                MDC.clear();
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
