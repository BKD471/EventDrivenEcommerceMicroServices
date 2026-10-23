package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;

/** Logs every skipped import row with the reason, so a partially imported file can be corrected and resent. */
@Slf4j
@RequiredArgsConstructor
public class CatalogSkipListener implements SkipListener<ProductCsvRow, Product> {

    private final String jobName;

    @Override
    public void onSkipInRead(final Throwable t) {
        log.warn("Catalog batch job={} skipped an unreadable row: {}", jobName, t.getMessage());
    }

    @Override
    public void onSkipInProcess(final ProductCsvRow item, final Throwable t) {
        log.warn("Catalog batch job={} skipped row {}: {}", jobName, item, t.getMessage());
    }
}
