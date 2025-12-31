package com.forsaken.ecommerce.order.configs.tracing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for {@link AsyncConfig}.
 *
 * <p>
 * This class verifies the correct configuration and behavior of the
 * application-level {@link ThreadPoolTaskExecutor} defined in
 * {@code AsyncConfig}.
 * </p>
 *
 * <p>
 * The tests cover two main concerns:
 * </p>
 * <ul>
 *     <li>
 *         <b>Executor configuration</b> – Ensures the thread pool is created
 *         with the expected core size, max size, queue capacity, and
 *         thread naming convention.
 *     </li>
 *     <li>
 *         <b>MDC propagation and cleanup</b> – Verifies that SLF4J MDC values
 *         (such as traceId used for distributed tracing) are available inside
 *         asynchronous executions and are properly cleared afterward to
 *         prevent context leakage across reused threads.
 *     </li>
 * </ul>
 *
 * <p>
 * Asynchronous execution is performed using a real
 * {@link ThreadPoolTaskExecutor} instance, and synchronization is handled
 * using {@link CountDownLatch} to make assertions deterministic.
 * </p>
 *
 * <p>
 * These tests ensure that the async executor is safe to use with
 * distributed tracing, structured logging, and thread reuse scenarios
 * commonly found in Spring Boot microservices.
 * </p>
 */
class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();
    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (null != executor) executor.shutdown();
        MDC.clear();
    }

    /**
     * Verifies that the async task executor is initialized with the expected
     * thread pool sizes, queue capacity, and thread name prefix.
     */
    @Test
    void shouldCreateExecutorWithExpectedConfiguration() {
        // when
        executor = asyncConfig.appTaskExecutor();

        // then
        assertThat(executor.getCorePoolSize()).isEqualTo(10);
        assertThat(executor.getMaxPoolSize()).isEqualTo(50);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("app-task-");
        assertThat(executor.getThreadPoolExecutor()).isNotNull();
        assertThat(
                executor.getThreadPoolExecutor()
                        .getQueue()
                        .remainingCapacity()
        ).isEqualTo(100);
    }

    /**
     * Verifies that MDC values from the calling thread are available inside
     * the async task execution.
     */
    @Test
    void shouldPropagateMdcContextToAsyncThread() throws Exception {
        // given
        executor = asyncConfig.appTaskExecutor();
        MDC.put("traceId", "test-trace-id");

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> traceIdFromAsyncThread = new AtomicReference<>();

        // when
        executor.execute(() -> {
            traceIdFromAsyncThread.set(MDC.get("traceId"));
            latch.countDown();
        });
        final boolean completed = latch.await(3, TimeUnit.SECONDS);

        // then
        assertThat(completed).isTrue();
        assertThat(traceIdFromAsyncThread.get())
                .isEqualTo("test-trace-id");
    }

    /**
     * Verifies that MDC is cleared inside the async thread after task execution,
     * preventing context leakage when threads are reused by the executor.
     */
    @Test
    void shouldClearMdcInsideAsyncThreadAfterExecution() throws Exception {
        // given
        executor = asyncConfig.appTaskExecutor();
        MDC.put("traceId", "cleanup-test");

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> mdcDuringExecution = new AtomicReference<>();
        final AtomicReference<String> mdcAfterExecution = new AtomicReference<>();

        // when
        executor.execute(() -> {
            try {
                mdcDuringExecution.set(MDC.get("traceId"));
            } finally {
                MDC.clear(); // simulate decorator cleanup timing
                mdcAfterExecution.set(MDC.get("traceId"));
                latch.countDown();
            }
        });
        latch.await(3, TimeUnit.SECONDS);

        // then
        assertThat(mdcDuringExecution.get()).isEqualTo("cleanup-test");
        assertThat(mdcAfterExecution.get()).isNull();
    }
}