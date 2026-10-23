package com.forsaken.ecommerce.payment.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Reads ids in ascending order using keyset paging. The last id read is saved in the step's execution context,
 * so a restarted job continues after it.
 */
public class KeysetIdReader implements ItemStreamReader<Long> {

    static final String LAST_READ_ID = "outbox.last.read.id";

    /** Fetches up to {@code limit} ids greater than {@code afterId}, in ascending order. */
    @FunctionalInterface
    public interface PageSource {
        List<Long> fetch(long afterId, int limit);
    }

    private final PageSource source;
    private final int pageSize;
    private final Deque<Long> buffer = new ArrayDeque<>();

    private long lastFetchedId;
    private long lastReadId;
    private boolean exhausted;

    public KeysetIdReader(final PageSource source, final int pageSize) {
        this.source = source;
        this.pageSize = pageSize;
    }

    @Override
    public void open(final ExecutionContext executionContext) {
        lastReadId = executionContext.getLong(LAST_READ_ID, 0L);
        lastFetchedId = lastReadId;
        exhausted = false;
        buffer.clear();
    }

    @Override
    public Long read() {
        if (buffer.isEmpty() && !exhausted) {
            final List<Long> page = source.fetch(lastFetchedId, pageSize);
            if (page.size() < pageSize) {
                exhausted = true;
            }
            if (!page.isEmpty()) {
                buffer.addAll(page);
                lastFetchedId = page.get(page.size() - 1);
            }
        }
        final Long next = buffer.poll();
        if (next != null) {
            lastReadId = next;
        }
        return next;
    }

    @Override
    public void update(final ExecutionContext executionContext) {
        executionContext.putLong(LAST_READ_ID, lastReadId);
    }

    @Override
    public void close() {
        buffer.clear();
    }
}
