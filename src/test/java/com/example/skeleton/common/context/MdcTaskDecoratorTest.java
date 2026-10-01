package com.example.skeleton.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void propagatesMdcToAnotherThread() throws Exception {
        MDC.put("requestId", "parent-id");
        AtomicReference<String> seenInChild = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seenInChild.set(MDC.get("requestId")));

        runInNewThread(decorated);

        assertThat(seenInChild.get()).isEqualTo("parent-id");
    }

    @Test
    void clearsExecutionThreadMdcAfterTheTask() throws Exception {
        MDC.put("requestId", "parent-id");
        Runnable decorated = decorator.decorate(() -> {
        });
        AtomicReference<String> afterTask = new AtomicReference<>();

        runInNewThread(() -> {
            decorated.run();
            afterTask.set(MDC.get("requestId"));
        });

        assertThat(afterTask.get())
                .as("pooled threads are reused — stale MDC must not survive the task")
                .isNull();
    }

    @Test
    void emptyParentContextClearsAnyStaleChildContext() throws Exception {
        AtomicReference<String> seenByTask = new AtomicReference<>();
        Runnable decoratedWithoutParentContext = decorator.decorate(() -> seenByTask.set(MDC.get("requestId")));

        runInNewThread(() -> {
            MDC.put("requestId", "stale-from-previous-task");
            decoratedWithoutParentContext.run();
        });

        assertThat(seenByTask.get())
                .as("a task submitted without MDC must not inherit a pooled thread's leftovers")
                .isNull();
    }

    private static void runInNewThread(Runnable runnable) throws InterruptedException {
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();
    }
}
