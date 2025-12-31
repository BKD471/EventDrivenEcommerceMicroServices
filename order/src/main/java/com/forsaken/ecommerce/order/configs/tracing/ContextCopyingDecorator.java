package com.forsaken.ecommerce.order.configs.tracing;

import io.micrometer.context.ContextSnapshot;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

import static io.micrometer.context.ContextSnapshot.Scope;

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
                if (null != mdcContext) MDC.clear();
            }
        };
    }
}