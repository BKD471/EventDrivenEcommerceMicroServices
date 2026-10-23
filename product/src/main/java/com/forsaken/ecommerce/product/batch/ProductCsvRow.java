package com.forsaken.ecommerce.product.batch;

/** One raw, not yet validated, CSV record of the import file. */
public record ProductCsvRow(
        String id,
        String name,
        String description,
        String availableQuantity,
        String price,
        String categoryId,
        String imageUrl
) {
}
