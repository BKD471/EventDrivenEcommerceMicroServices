package com.forsaken.ecommerce.order.order.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestTest {

    @Test
    void toOrderDoesNotCopyClientSuppliedId() {
        final OrderRequest request = OrderRequest.builder()
                .id(42)
                .build();

        assertThat(request.toOrder().getId()).isNull();
    }
}
