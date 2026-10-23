package com.forsaken.ecommerce.order.batch;

import com.forsaken.ecommerce.order.order.model.OrderSagaStatus;
import com.forsaken.ecommerce.order.order.repository.IOrderRepository;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * Reads ids of claimable orders in ascending id order using keyset paging. The last id read is saved in the
 * step's execution context, so a restarted job continues after it instead of reloading everything.
 */
public class ClaimableOrderIdReader implements ItemStreamReader<Integer> {

    static final String LAST_READ_ID = "claimable.order.last.read.id";

    private final IOrderRepository orderRepository;
    private final Collection<OrderSagaStatus> statuses;
    private final int pageSize;
    private final Deque<Integer> buffer = new ArrayDeque<>();

    private int lastFetchedId;
    private int lastReadId;
    private boolean exhausted;

    public ClaimableOrderIdReader(
            final IOrderRepository orderRepository,
            final Collection<OrderSagaStatus> statuses,
            final int pageSize
    ) {
        this.orderRepository = orderRepository;
        this.statuses = statuses;
        this.pageSize = pageSize;
    }

    @Override
    public void open(final ExecutionContext executionContext) {
        lastReadId = executionContext.getInt(LAST_READ_ID, 0);
        lastFetchedId = lastReadId;
        exhausted = false;
        buffer.clear();
    }

    @Override
    public Integer read() {
        if (buffer.isEmpty() && !exhausted) {
            final List<Integer> page = orderRepository.findClaimableIdsBySagaStatus(
                    statuses, LocalDateTime.now(), lastFetchedId, PageRequest.of(0, pageSize));
            if (page.size() < pageSize) {
                exhausted = true;
            }
            if (!page.isEmpty()) {
                buffer.addAll(page);
                lastFetchedId = page.get(page.size() - 1);
            }
        }
        final Integer next = buffer.poll();
        if (next != null) {
            lastReadId = next;
        }
        return next;
    }

    @Override
    public void update(final ExecutionContext executionContext) {
        executionContext.putInt(LAST_READ_ID, lastReadId);
    }

    @Override
    public void close() {
        buffer.clear();
    }
}
