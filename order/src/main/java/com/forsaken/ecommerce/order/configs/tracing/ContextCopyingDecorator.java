package com.forsaken.ecommerce.order.configs.tracing;

import io.micrometer.context.ContextSnapshot;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

import static io.micrometer.context.ContextSnapshot.Scope;

/**
 * {@link TaskDecorator} that captures and propagates contextual information from the
 * calling thread to asynchronous tasks executed on other threads.
 * <p>
 * This decorator:
 * <ul>
 *     <li>Captures Micrometer tracing context (such as {@code traceId}, {@code spanId},
 *     and baggage) using {@link ContextSnapshot} and restores it for the async task,</li>
 *     <li>Copies the SLF4J {@link MDC} logging context so log entries from async code
 *     can be correlated with the originating request, and</li>
 *     <li>Propagates Spring Web {@link RequestAttributes} via {@link RequestContextHolder}
 *     so request-scoped data remains available in async execution.</li>
 * </ul>
 * This propagation is essential for consistent distributed tracing and logging correlation
 * when using asynchronous execution (for example with {@code @Async} or thread pools).
 */
public class ContextCopyingDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(final Runnable runnable) {
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        final Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        // Capture Micrometer context (traceId, spanId, baggage, etc.)
        final ContextSnapshot snapshot = ContextSnapshot.captureAll();
        return () -> {
            try (final Scope ignored = snapshot.setThreadLocals()) {
                if (null != requestAttributes) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                if (null != mdcContext) {
                    MDC.setContextMap(mdcContext);
                }
                runnable.run();
            } finally {
                RequestContextHolder.resetRequestAttributes();
                MDC.clear();
            }
        };
    }
}