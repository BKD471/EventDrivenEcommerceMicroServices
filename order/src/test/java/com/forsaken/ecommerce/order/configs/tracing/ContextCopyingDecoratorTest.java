package com.forsaken.ecommerce.order.configs.tracing;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Test suite for {@link ContextCopyingDecorator}.
 *
 * <p>
 * This test class verifies that {@code ContextCopyingDecorator} correctly
 * captures and propagates thread-bound execution context from the calling
 * thread to an asynchronous thread, and also ensures proper cleanup after
 * execution.
 * </p>
 *
 * <p>
 * The following execution contexts are covered:
 * </p>
 * <ul>
 *     <li><b>SLF4J MDC</b> – Ensures logging context (e.g. traceId) is available
 *     in async execution and cleared afterward to prevent context leakage.</li>
 *     <li><b>Spring RequestContextHolder</b> – Verifies that web request-scoped
 *     {@link org.springframework.web.context.request.RequestAttributes}
 *     are propagated to asynchronous threads.</li>
 *     <li><b>Micrometer ContextSnapshot</b> – Confirms that Micrometer-managed
 *     ThreadLocal values (used by distributed tracing frameworks like
 *     OpenTelemetry) are correctly captured and restored.</li>
 * </ul>
 *
 * <p>
 * Each test executes the decorated {@link Runnable} in a real asynchronous
 * {@link Thread} and uses synchronization primitives to ensure deterministic
 * assertions.
 * </p>
 *
 * <p>
 * A cleanup step runs after each test to clear MDC and reset request attributes
 * to guarantee test isolation and prevent cross-test contamination.
 * </p>
 *
 * <p>
 * These tests collectively ensure that {@code ContextCopyingDecorator} is safe
 * and reliable for use with asynchronous execution mechanisms such as
 * {@code @Async}, {@code CompletableFuture}, or custom thread pools in a
 * Spring-based microservice architecture.
 * </p>
 */
class ContextCopyingDecoratorTest {

    private final ContextCopyingDecorator decorator = new ContextCopyingDecorator();

    @AfterEach
    void cleanup() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Verifies that MDC context is propagated to async thread.
     */
    @Test
    void shouldPropagateMdcContextToAsyncThread() throws Exception {
        // given
        MDC.put("traceId", "test-trace");
        final AtomicReference<String> traceIdFromAsyncThread = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable decorated = decorator.decorate(() -> {
            traceIdFromAsyncThread.set(MDC.get("traceId"));
            latch.countDown();
        });

        // when
        new Thread(decorated).start();
        latch.await(2, TimeUnit.SECONDS);

        // then
        assertThat(traceIdFromAsyncThread.get()).isEqualTo("test-trace");
    }

    /**
     * Verifies that MDC is cleared after async execution (inside async thread).
     */
    @Test
    void shouldClearMdcAfterAsyncExecution() throws Exception {
        // given
        MDC.put("traceId", "cleanup-test");

        final AtomicReference<String> mdcAfterExecution = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable decorated = decorator.decorate(() -> {
            // no-op
        });

        // when
        new Thread(() -> {
            decorated.run();
            mdcAfterExecution.set(MDC.get("traceId"));
            latch.countDown();
        }).start();
        latch.await(2, TimeUnit.SECONDS);

        // then
        assertThat(mdcAfterExecution.get()).isNull();
    }

    /**
     * Verifies that RequestAttributes are propagated to async thread.
     */
    @Test
    void shouldPropagateRequestAttributes() throws Exception {
        // given
        final RequestAttributes requestAttributes = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(requestAttributes);

        final AtomicReference<RequestAttributes> attrsFromAsyncThread = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable decorated = decorator.decorate(() -> {
            attrsFromAsyncThread.set(RequestContextHolder.getRequestAttributes());
            latch.countDown();
        });

        // when
        new Thread(decorated).start();
        latch.await(2, TimeUnit.SECONDS);

        // then
        assertThat(attrsFromAsyncThread.get()).isSameAs(requestAttributes);
    }

    /**
     * Verifies that Micrometer ContextSnapshot is applied in async thread.
     * This test uses a custom ThreadLocalAccessor to simulate tracing context.
     */
    @Test
    void shouldPropagateMicrometerContext() throws Exception {
        // given
        final ThreadLocal<String> tracingContext = new ThreadLocal<>();
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                new ThreadLocalAccessor<String>() {

                    @Override
                    public Object key() {
                        return "test-trace-context";
                    }

                    @Override
                    public String getValue() {
                        return tracingContext.get();
                    }

                    @Override
                    public void setValue(String value) {
                        tracingContext.set(value);
                    }

                    @Override
                    public void reset() {
                        tracingContext.remove();
                    }
                }
        );
        tracingContext.set("micrometer-trace");

        final AtomicReference<String> valueFromAsyncThread = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Runnable decorated = decorator.decorate(() -> {
            valueFromAsyncThread.set(tracingContext.get());
            latch.countDown();
        });

        // when
        new Thread(decorated).start();
        latch.await(2, TimeUnit.SECONDS);

        // then
        assertThat(valueFromAsyncThread.get()).isEqualTo("micrometer-trace");
    }
}