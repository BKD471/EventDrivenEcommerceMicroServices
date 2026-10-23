package com.forsaken.ecommerce.order.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DayWindowReaderTest {

    private final LocalDate today = LocalDate.of(2026, 1, 10);

    @Test
    void emitsLastCompleteDaysOldestFirstExcludingToday() {
        final DayWindowReader reader = new DayWindowReader(3, () -> today);
        reader.open(new ExecutionContext());

        assertEquals(LocalDate.of(2026, 1, 7), reader.read());
        assertEquals(LocalDate.of(2026, 1, 8), reader.read());
        assertEquals(LocalDate.of(2026, 1, 9), reader.read());
        assertNull(reader.read());
    }

    @Test
    void resumesAfterTheSavedDay() {
        final ExecutionContext context = new ExecutionContext();
        final DayWindowReader first = new DayWindowReader(3, () -> today);
        first.open(context);
        first.read();
        first.update(context);

        final DayWindowReader restarted = new DayWindowReader(3, () -> today);
        restarted.open(context);

        assertEquals(LocalDate.of(2026, 1, 8), restarted.read());
    }
}
