package com.forsaken.ecommerce.order.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringKeysetReaderTest {

    @Test
    void resumesFromLastSavedReference() throws Exception {
        final List<String> references = List.of("order-1", "order-2", "order-3");
        final var reader = new StringKeysetReader<String>(
                "lastReference",
                (after, size) -> references.stream().filter(reference -> reference.compareTo(after) > 0)
                        .limit(size).toList(),
                reference -> reference,
                2);
        final ExecutionContext context = new ExecutionContext();

        reader.open(context);
        assertEquals("order-1", reader.read());
        reader.update(context);
        reader.close();

        reader.open(context);
        assertEquals("order-2", reader.read());
        assertEquals("order-3", reader.read());
        assertNull(reader.read());
        reader.close();
    }
}
