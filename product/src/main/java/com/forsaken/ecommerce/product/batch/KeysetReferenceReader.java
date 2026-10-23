package com.forsaken.ecommerce.product.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Reads order references in ascending order using keyset paging. The last reference read is saved in the step's
 * execution context, so a restarted job continues after it.
 */
public class KeysetReferenceReader implements ItemStreamReader<String> {

    static final String LAST_READ = "reservation.last.read.reference";

    /** Fetches up to {@code limit} references greater than {@code after}, in ascending order. */
    @FunctionalInterface
    public interface PageSource {
        List<String> fetch(String after, int limit);
    }

    private final PageSource source;
    private final int pageSize;
    private final Deque<String> buffer = new ArrayDeque<>();

    private String lastFetched = "";
    private String lastRead = "";
    private boolean exhausted;

    public KeysetReferenceReader(final PageSource source, final int pageSize) {
        this.source = source;
        this.pageSize = pageSize;
    }

    @Override
    public void open(final ExecutionContext executionContext) {
        lastRead = executionContext.getString(LAST_READ, "");
        lastFetched = lastRead;
        exhausted = false;
        buffer.clear();
    }

    @Override
    public String read() {
        if (buffer.isEmpty() && !exhausted) {
            final List<String> page = source.fetch(lastFetched, pageSize);
            if (page.size() < pageSize) {
                exhausted = true;
            }
            if (!page.isEmpty()) {
                buffer.addAll(page);
                lastFetched = page.get(page.size() - 1);
            }
        }
        final String next = buffer.poll();
        if (next != null) {
            lastRead = next;
        }
        return next;
    }

    @Override
    public void update(final ExecutionContext executionContext) {
        executionContext.putString(LAST_READ, lastRead);
    }

    @Override
    public void close() {
        buffer.clear();
    }
}
