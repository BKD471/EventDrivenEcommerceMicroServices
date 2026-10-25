package com.forsaken.ecommerce.order.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/** Restartable keyset-paged reader for items with an ascending string key. */
public class StringKeysetReader<T> implements ItemStreamReader<T> {

    @FunctionalInterface
    public interface PageFetcher<T> {
        List<T> fetch(String afterKey, int pageSize);
    }

    private final String contextKey;
    private final PageFetcher<T> fetcher;
    private final Function<T, String> keyOf;
    private final int pageSize;
    private final Deque<T> buffer = new ArrayDeque<>();

    private String lastFetchedKey = "";
    private String lastReadKey = "";
    private boolean exhausted;

    public StringKeysetReader(
            final String contextKey,
            final PageFetcher<T> fetcher,
            final Function<T, String> keyOf,
            final int pageSize
    ) {
        this.contextKey = contextKey;
        this.fetcher = fetcher;
        this.keyOf = keyOf;
        this.pageSize = pageSize;
    }

    @Override
    public void open(final ExecutionContext executionContext) {
        lastReadKey = executionContext.getString(contextKey, "");
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
                lastFetchedKey = keyOf.apply(page.get(page.size() - 1));
            }
        }
        final T next = buffer.poll();
        if (next != null) {
            lastReadKey = keyOf.apply(next);
        }
        return next;
    }

    @Override
    public void update(final ExecutionContext executionContext) {
        executionContext.putString(contextKey, lastReadKey);
    }

    @Override
    public void close() {
        buffer.clear();
    }
}
