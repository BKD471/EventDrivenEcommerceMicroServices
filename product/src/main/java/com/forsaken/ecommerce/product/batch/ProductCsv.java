package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.Product;

/** CSV layout shared by the catalog export and import jobs. */
public final class ProductCsv {

    public static final String[] COLUMNS =
            {"id", "name", "description", "availableQuantity", "price", "categoryId", "imageUrl"};
    public static final String HEADER = String.join(",", COLUMNS);

    private ProductCsv() {
    }

    public static String format(final Product product) {
        return String.join(",",
                String.valueOf(product.getId()),
                escape(product.getName()),
                escape(product.getDescription()),
                String.valueOf(product.getAvailableQuantity()),
                product.getPrice() == null ? "" : product.getPrice().toPlainString(),
                product.getCategory() == null ? "" : String.valueOf(product.getCategory().getId()),
                escape(product.getImageUrl()));
    }

    /** Quotes a value when needed and neutralises line breaks, which the line-based reader cannot carry. */
    static String escape(final String value) {
        if (value == null) {
            return "";
        }
        final String flat = value.replace("\r", " ").replace("\n", " ");
        if (flat.contains(",") || flat.contains("\"")) {
            return "\"" + flat.replace("\"", "\"\"") + "\"";
        }
        return flat;
    }
}
