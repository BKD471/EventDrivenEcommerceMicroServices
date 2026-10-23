package com.forsaken.ecommerce.order.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Emits the last {@code lookbackDays} complete days (oldest first, today excluded). The last day read is saved
 * in the execution context, so a restarted run continues after it.
 */
public class DayWindowReader implements ItemStreamReader<LocalDate> {

    static final String LAST_DAY = "day.window.last.read";

    private final int lookbackDays;
    private final Supplier<LocalDate> today;
    private final Deque<LocalDate> days = new ArrayDeque<>();
    private LocalDate lastRead;

    public DayWindowReader(final int lookbackDays, final Supplier<LocalDate> today) {
        this.lookbackDays = lookbackDays;
        this.today = today;
    }

    @Override
    public void open(final ExecutionContext executionContext) {
        days.clear();
        final String saved = executionContext.getString(LAST_DAY, null);
        lastRead = saved == null ? null : LocalDate.parse(saved);
        final LocalDate end = today.get().minusDays(1);
        for (LocalDate day = end.minusDays(lookbackDays - 1L); !day.isAfter(end); day = day.plusDays(1)) {
            if (lastRead == null || day.isAfter(lastRead)) {
                days.add(day);
            }
        }
    }

    @Override
    public LocalDate read() {
        final LocalDate next = days.poll();
        if (next != null) {
            lastRead = next;
        }
        return next;
    }

    @Override
    public void update(final ExecutionContext executionContext) {
        if (lastRead != null) {
            executionContext.putString(LAST_DAY, lastRead.toString());
        }
    }

    @Override
    public void close() {
        days.clear();
    }
}
