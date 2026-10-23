package com.forsaken.ecommerce.order.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeysetReaderTest {

    private static final List<Integer> DATA = IntStream.rangeClosed(1, 5).boxed().toList();

    private KeysetReader<Integer> reader() {
        return new KeysetReader<>("k",
                (after, size) -> DATA.stream().filter(i -> i > after).limit(size).toList(),
                Integer::intValue, 2);
    }

    @Test
    void readsEveryItemAcrossPages() {
        final KeysetReader<Integer> reader = reader();
        reader.open(new ExecutionContext());

        for (int expected = 1; expected <= 5; expected++) {
            assertEquals(expected, reader.read());
        }
        assertNull(reader.read());
    }

    @Test
    void resumesAfterTheSavedKey() {
        final ExecutionContext context = new ExecutionContext();
        final KeysetReader<Integer> first = reader();
        first.open(context);
        first.read();
        first.read();
        first.read();
        first.update(context);

        final KeysetReader<Integer> restarted = reader();
        restarted.open(context);

        assertEquals(4, restarted.read());
    }
}
