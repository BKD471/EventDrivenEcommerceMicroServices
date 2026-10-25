package com.forsaken.ecommerce.product.controller;

import com.forsaken.ecommerce.product.configs.security.ServiceSecurityConfiguration;
import com.forsaken.ecommerce.product.service.IProductService;
import com.forsaken.ecommerce.product.service.IS3Service;
import com.forsaken.ecommerce.product.service.ReservationReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductControllerImpl.class)
@Import(ServiceSecurityConfiguration.class)
@TestPropertySource(properties = {
        "ORDER_SERVICE_PASSWORD=test-secret",
        "batch.metadata.purge.enabled=false"
})
class ProductServiceSecurityTest {

    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString("order-service:test-secret".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IProductService productService;

    @MockBean
    private IS3Service s3Service;

    @MockBean
    private ReservationReconciliationService reservationReconciliationService;

    @Test
    void releaseRequiresOrderServiceCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/products/reservations/order-42/release"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService);
    }

    @Test
    void orderServiceCanReleaseReservation() throws Exception {
        mockMvc.perform(post("/api/v1/products/reservations/order-42/release")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("X-CSRF-TOKEN", "test-secret"))
                .andExpect(status().isOk());

        verify(productService).releaseReservation("order-42");
    }

    @Test
    void orderServiceCannotReleaseWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/products/reservations/order-42/release")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void reservationReconciliationRequiresOrderServiceCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/products/reservations/order-42/reconciliation"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationReconciliationService);
    }

    @Test
    void orderServiceCanReadReservationReconciliation() throws Exception {
        mockMvc.perform(get("/api/v1/products/reservations/order-42/reconciliation")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk());

        verify(reservationReconciliationService).findByOrderReference("order-42");
    }

    @Test
    void reservationReconciliationPageRequiresOrderServiceCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/products/reservations/reconciliation")
                        .param("olderThan", "2026-01-01T00:00:00Z"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationReconciliationService);
    }

    @Test
    void orderServiceCanReadReservationReconciliationPage() throws Exception {
        mockMvc.perform(get("/api/v1/products/reservations/reconciliation")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .param("olderThan", "2026-01-01T00:00:00Z"))
                .andExpect(status().isOk());

        verify(reservationReconciliationService)
                .findReservedCreatedBefore(java.time.Instant.parse("2026-01-01T00:00:00Z"), "", 100);
    }
}
