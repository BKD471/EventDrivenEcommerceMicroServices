package com.forsaken.ecommerce.payment.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeysetIdReaderTest {

    private static final List<Long> IDS = List.of(1L, 3L, 7L, 9L);

    private final List<Long> requestedAfter = new ArrayList<>();

    private List<Long> fetch(final long afterId, final int limit) {
        requestedAfter.add(afterId);
        return IDS.stream().filter(id -> id > afterId).limit(limit).toList();
    }

    @Test
    void shouldPageByKeysetUntilExhausted() {
        final KeysetIdReader reader = new KeysetIdReader(this::fetch, 2);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isEqualTo(1L);
        assertThat(reader.read()).isEqualTo(3L);
        assertThat(reader.read()).isEqualTo(7L);
        assertThat(reader.read()).isEqualTo(9L);
        assertThat(reader.read()).isNull();
        assertThat(requestedAfter).containsExactly(0L, 3L, 9L);
    }

    @Test
    void shouldResumeAfterLastReadIdOnRestart() {
        final ExecutionContext context = new ExecutionContext();
        context.putLong(KeysetIdReader.LAST_READ_ID, 3L);
        final KeysetIdReader reader = new KeysetIdReader(this::fetch, 10);
        reader.open(context);

        assertThat(reader.read()).isEqualTo(7L);
        reader.update(context);

        assertThat(context.getLong(KeysetIdReader.LAST_READ_ID)).isEqualTo(7L);
        assertThat(requestedAfter).containsExactly(3L);
    }
}
