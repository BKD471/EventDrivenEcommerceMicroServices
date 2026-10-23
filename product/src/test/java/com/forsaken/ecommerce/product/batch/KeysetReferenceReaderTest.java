package com.forsaken.ecommerce.product.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeysetReferenceReaderTest {

    private static final List<String> REFS = List.of("A", "C", "E", "G");

    private final List<String> requestedAfter = new ArrayList<>();

    private List<String> fetch(final String after, final int limit) {
        requestedAfter.add(after);
        return REFS.stream().filter(r -> r.compareTo(after) > 0).limit(limit).toList();
    }

    @Test
    void shouldPageByKeysetUntilExhausted() {
        final KeysetReferenceReader reader = new KeysetReferenceReader(this::fetch, 2);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isEqualTo("A");
        assertThat(reader.read()).isEqualTo("C");
        assertThat(reader.read()).isEqualTo("E");
        assertThat(reader.read()).isEqualTo("G");
        assertThat(reader.read()).isNull();
        assertThat(requestedAfter).containsExactly("", "C", "G");
    }

    @Test
    void shouldResumeAfterLastReadReferenceOnRestart() {
        final ExecutionContext context = new ExecutionContext();
        context.putString(KeysetReferenceReader.LAST_READ, "C");
        final KeysetReferenceReader reader = new KeysetReferenceReader(this::fetch, 10);
        reader.open(context);

        assertThat(reader.read()).isEqualTo("E");
        reader.update(context);

        assertThat(context.getString(KeysetReferenceReader.LAST_READ)).isEqualTo("E");
        assertThat(requestedAfter).containsExactly("C");
    }
}
