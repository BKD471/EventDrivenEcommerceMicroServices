package com.forsaken.ecommerce.product.batch;

/** A single import row is invalid; the row is skipped and the rest of the file continues. */
public class ProductImportException extends RuntimeException {

    public ProductImportException(final String message) {
        super(message);
    }
}
