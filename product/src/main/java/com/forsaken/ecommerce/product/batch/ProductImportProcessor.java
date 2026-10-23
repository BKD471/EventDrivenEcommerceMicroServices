package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import com.forsaken.ecommerce.product.repository.ICategoryRepository;
import com.forsaken.ecommerce.product.repository.IProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Validates a CSV row and turns it into a product to save. A blank id creates a product; an id that exists
 * updates its descriptive fields and price but never its stock, because stock is owned by purchases and
 * reservations and a file would overwrite concurrent changes.
 */
@RequiredArgsConstructor
public class ProductImportProcessor implements ItemProcessor<ProductCsvRow, Product> {

    private final IProductRepository productRepository;
    private final ICategoryRepository categoryRepository;

    @Override
    public Product process(final ProductCsvRow row) {
        final String name = required(row.name(), "name");
        final String description = required(row.description(), "description");
        final BigDecimal price = parsePrice(row.price());
        final Category category = categoryRepository.findById(parseInt(row.categoryId(), "categoryId"))
                .orElseThrow(() -> new ProductImportException("Unknown categoryId " + row.categoryId()));
        final String imageUrl = blankToNull(row.imageUrl());

        if (isBlank(row.id())) {
            return Product.builder()
                    .name(name)
                    .description(description)
                    .availableQuantity(parseQuantity(row.availableQuantity()))
                    .price(price)
                    .category(category)
                    .imageUrl(imageUrl)
                    .additionDate(LocalDateTime.now())
                    .build();
        }
        final int id = parseInt(row.id(), "id");
        final Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductImportException("Unknown product id " + id));
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        return product;
    }

    private static String required(final String value, final String field) {
        if (isBlank(value)) {
            throw new ProductImportException(field + " is required");
        }
        return value.trim();
    }

    private static int parseInt(final String value, final String field) {
        try {
            return Integer.parseInt(required(value, field));
        } catch (NumberFormatException ex) {
            throw new ProductImportException(field + " is not a whole number: " + value);
        }
    }

    private static BigDecimal parsePrice(final String value) {
        try {
            final BigDecimal price = new BigDecimal(required(value, "price"));
            if (price.signum() <= 0) {
                throw new ProductImportException("price must be positive: " + value);
            }
            return price;
        } catch (NumberFormatException ex) {
            throw new ProductImportException("price is not a number: " + value);
        }
    }

    private static double parseQuantity(final String value) {
        try {
            final double quantity = Double.parseDouble(required(value, "availableQuantity"));
            if (quantity < 0 || Double.isNaN(quantity) || Double.isInfinite(quantity)) {
                throw new ProductImportException("availableQuantity must not be negative: " + value);
            }
            return quantity;
        } catch (NumberFormatException ex) {
            throw new ProductImportException("availableQuantity is not a number: " + value);
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(final String value) {
        return isBlank(value) ? null : value.trim();
    }
}
