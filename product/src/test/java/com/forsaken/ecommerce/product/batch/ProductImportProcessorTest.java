package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import com.forsaken.ecommerce.product.repository.ICategoryRepository;
import com.forsaken.ecommerce.product.repository.IProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductImportProcessorTest {

    private final IProductRepository products = mock(IProductRepository.class);
    private final ICategoryRepository categories = mock(ICategoryRepository.class);
    private final ProductImportProcessor processor = new ProductImportProcessor(products, categories);
    private final Category category = Category.builder().id(2).name("Office").build();

    private ProductCsvRow row(final String id, final String qty, final String price, final String categoryId) {
        return new ProductCsvRow(id, "Pen", "Blue pen", qty, price, categoryId, "");
    }

    @Test
    void blankIdCreatesAProductWithTheFileStock() {
        when(categories.findById(2)).thenReturn(Optional.of(category));

        final Product created = processor.process(row("", "12", "1.50", "2"));

        assertNull(created.getId());
        assertEquals(12.0, created.getAvailableQuantity());
        assertEquals(new BigDecimal("1.50"), created.getPrice());
        assertEquals(category, created.getCategory());
        assertNull(created.getImageUrl());
    }

    @Test
    void existingIdUpdatesFieldsButNeverStock() {
        when(categories.findById(2)).thenReturn(Optional.of(category));
        final Product existing = Product.builder().id(7).name("Old").description("Old").availableQuantity(40)
                .price(BigDecimal.ONE).build();
        when(products.findById(7)).thenReturn(Optional.of(existing));

        final Product updated = processor.process(row("7", "999", "2.00", "2"));

        assertEquals("Pen", updated.getName());
        assertEquals(new BigDecimal("2.00"), updated.getPrice());
        assertEquals(40.0, updated.getAvailableQuantity());
    }

    @Test
    void rejectsUnknownProductId() {
        when(categories.findById(2)).thenReturn(Optional.of(category));
        when(products.findById(7)).thenReturn(Optional.empty());

        assertThrows(ProductImportException.class, () -> processor.process(row("7", "1", "1", "2")));
    }

    @Test
    void rejectsUnknownCategory() {
        when(categories.findById(9)).thenReturn(Optional.empty());

        assertThrows(ProductImportException.class, () -> processor.process(row("", "1", "1", "9")));
    }

    @Test
    void rejectsInvalidNumbers() {
        when(categories.findById(2)).thenReturn(Optional.of(category));

        assertThrows(ProductImportException.class, () -> processor.process(row("", "1", "abc", "2")));
        assertThrows(ProductImportException.class, () -> processor.process(row("", "1", "-1", "2")));
        assertThrows(ProductImportException.class, () -> processor.process(row("", "-1", "1", "2")));
        assertThrows(ProductImportException.class, () -> processor.process(row("", "x", "1", "2")));
        assertThrows(ProductImportException.class, () -> processor.process(row("", "1", "1", "")));
    }

    @Test
    void rejectsMissingName() {
        assertThrows(ProductImportException.class, () -> processor.process(
                new ProductCsvRow("", " ", "d", "1", "1", "2", "")));
    }
}
