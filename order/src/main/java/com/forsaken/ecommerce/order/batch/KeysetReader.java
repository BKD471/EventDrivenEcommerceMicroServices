package com.forsaken.ecommerce.order.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Generic keyset-paged reader over items with an ascending integer key. The last key read is saved in the
 * step's execution context, so a restarted job continues after it.
 */
public class KeysetReader<T> implements ItemStreamReader<T> {

    /** Fetches the next page of items whose key is greater than {@code afterKey}. */
    @FunctionalInterface
    public interface PageFetcher<T> {
        List<T> fetch(int afterKey, int pageSize);
    }

    private final String contextKey;
    private final PageFetcher<T> fetcher;
    private final ToIntFunction<T> keyOf;
    private final int pageSize;
    private final Deque<T> buffer = new ArrayDeque<>();

    private int lastFetchedKey;
    private int lastReadKey;
    private boolean exhausted;

    public KeysetReader(
            final String contextKey,
            final PageFetcher<T> fetcher,
            final ToIntFunction<T> keyOf,
            final int pageSize
    ) {
        this.contextKey = contextKey;
        this.fetcher = fetcher;
        this.keyOf = keyOf;
        this.pageSize = pageSize;
    }

    @Override
    public void open(final ExecutionContext executionContext) {
        lastReadKey = executionContext.getInt(contextKey, 0);
        lastFetchedKey = lastReadKey;
        exhausted = false;
        buffer.clear();
    }

    @Override
    public T read() {
        if (buffer.isEmpty() && !exhausted) {
            final List<T> page = fetcher.fetch(lastFetchedKey, pageSize);
            if (page.size() < pageSize) {
                exhausted = true;
            }
            if (!page.isEmpty()) {
                buffer.addAll(page);
                lastFetchedKey = keyOf.applyAsInt(page.get(page.size() - 1));
            }
        }
        final T next = buffer.poll();
        if (next != null) {
            lastReadKey = keyOf.applyAsInt(next);
        }
        return next;
    }

    @Override
    public void update(final ExecutionContext executionContext) {
        executionContext.putInt(contextKey, lastReadKey);
    }

    @Override
    public void close() {
        buffer.clear();
    }
}
