package com.forsaken.ecommerce.payment.controller;

import com.forsaken.ecommerce.payment.configs.security.ServiceSecurityConfiguration;
import com.forsaken.ecommerce.payment.service.IPaymentService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentControllerImpl.class)
@Import(ServiceSecurityConfiguration.class)
@TestPropertySource(properties = "ORDER_SERVICE_PASSWORD=test-secret")
class PaymentServiceSecurityTest {

    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString("order-service:test-secret".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IPaymentService paymentService;

    @Test
    void confirmationRequiresOrderServiceCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/payments/42/confirm"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void orderServiceCanConfirmAndCancelPayment() throws Exception {
        mockMvc.perform(post("/api/v1/payments/42/confirm")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("X-CSRF-TOKEN", "test-secret"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/42/cancel")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .header("X-CSRF-TOKEN", "test-secret"))
                .andExpect(status().isOk());

        verify(paymentService).confirmPayment(42);
        verify(paymentService).cancelPayment(42);
    }

    @Test
    void orderServiceCannotMutateWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/payments/42/confirm")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }
}
