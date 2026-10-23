package com.forsaken.ecommerce.product.batch;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogFilePurgeSchedulerTest {

    private final ProductFileStorage storage = mock(ProductFileStorage.class);
    private final CatalogBatchProperties batch = new CatalogBatchProperties(
            true, 100, 3, 100, 10, 3,
            "imports/incoming/", "imports/processed/", "imports/failed/", "exports/");
    private final CatalogFilePurgeScheduler scheduler = new CatalogFilePurgeScheduler(storage, batch,
            new CatalogFilePurgeProperties(true, Duration.ofDays(14), Duration.ofDays(30), Duration.ofDays(90)),
            new SimpleMeterRegistry());

    private ProductFileStorage.StoredFile file(final String key, final long ageDays) {
        return new ProductFileStorage.StoredFile(key, Instant.now().minus(Duration.ofDays(ageDays)));
    }

    @Test
    void deletesOnlyFilesOlderThanRetention() {
        when(storage.list("exports/")).thenReturn(List.of(file("exports/old.csv", 20), file("exports/new.csv", 1)));

        assertEquals(1, scheduler.purgePrefix("exports/", Duration.ofDays(14)));

        verify(storage).delete("exports/old.csv");
        verify(storage, never()).delete("exports/new.csv");
    }

    @Test
    void oneFailedDeleteDoesNotStopTheRest() {
        when(storage.list("exports/")).thenReturn(List.of(file("exports/a.csv", 20), file("exports/b.csv", 20)));
        doThrow(new RuntimeException("boom")).when(storage).delete("exports/a.csv");

        assertEquals(1, scheduler.purgePrefix("exports/", Duration.ofDays(14)));

        verify(storage).delete("exports/b.csv");
    }

    @Test
    void listingFailureIsContained() {
        when(storage.list("exports/")).thenThrow(new RuntimeException("s3 down"));

        assertEquals(0, scheduler.purgePrefix("exports/", Duration.ofDays(14)));
    }

    @Test
    void purgeAppliesEachPrefixRetention() {
        when(storage.list("exports/")).thenReturn(List.of(file("exports/x.csv", 15)));
        when(storage.list("imports/processed/")).thenReturn(List.of(file("imports/processed/y.csv", 15)));
        when(storage.list("imports/failed/")).thenReturn(List.of(file("imports/failed/z.csv", 15)));

        scheduler.purge();

        verify(storage).delete("exports/x.csv");
        verify(storage, never()).delete("imports/processed/y.csv");
        verify(storage, never()).delete("imports/failed/z.csv");
    }
}
