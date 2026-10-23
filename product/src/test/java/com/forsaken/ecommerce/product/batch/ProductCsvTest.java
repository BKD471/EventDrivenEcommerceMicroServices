package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.model.Category;
import com.forsaken.ecommerce.product.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductCsvTest {

    @Test
    void formatsAProductInHeaderOrder() {
        final Product product = Product.builder().id(3).name("Pen").description("Blue").availableQuantity(5)
                .price(new BigDecimal("1.50")).category(Category.builder().id(2).build()).build();

        assertEquals("3,Pen,Blue,5.0,1.50,2,", ProductCsv.format(product));
    }

    @Test
    void quotesCommasAndQuotesAndFlattensLineBreaks() {
        assertEquals("\"a,b\"", ProductCsv.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", ProductCsv.escape("say \"hi\""));
        assertEquals("a b c", ProductCsv.escape("a\nb\r\nc".replace("\r\n", " ")));
        assertEquals("", ProductCsv.escape(null));
    }
}
