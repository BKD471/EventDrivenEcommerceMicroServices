package com.forsaken.ecommerce.notification.controller;

import com.forsaken.ecommerce.notification.models.PaymentMethod;
import com.forsaken.ecommerce.notification.service.IEmailService;
import com.forsaken.ecommerce.notification.service.IS3Service;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceControllerTest {

    private final IS3Service s3 = mock(IS3Service.class);
    private final IEmailService email = mock(IEmailService.class);
    private final InvoiceRegenerationRequest request = new InvoiceRegenerationRequest(
            "Ann Lee", "ann@example.com", new BigDecimal("10.00"), PaymentMethod.VISA, LocalDateTime.now());

    private InvoiceController controller(final String token) {
        return new InvoiceController(s3, email, token);
    }

    @Test
    void rejectsEveryCallWhenNoTokenIsConfigured() {
        assertEquals(HttpStatus.FORBIDDEN, controller("").exists("A1", "").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller("").regenerate("A1", "", request).getStatusCode());
        verify(email, never()).sendPaymentInvoice(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWrongOrMissingToken() {
        assertEquals(HttpStatus.FORBIDDEN, controller("t").exists("A1", "x").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller("t").exists("A1", null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller("t").regenerate("A1", "x", request).getStatusCode());
    }

    @Test
    void rejectsMalformedReference() {
        assertEquals(HttpStatus.BAD_REQUEST, controller("t").exists("../x", "t").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller("t").regenerate("a b", "t", request).getStatusCode());
    }

    @Test
    void reportsWhetherTheInvoiceExists() {
        when(s3.invoiceExists("A1")).thenReturn(true);

        assertEquals(Map.of("exists", true), controller("t").exists("A1", "t").getBody());
    }

    @Test
    void doesNotRegenerateAnExistingInvoice() {
        when(s3.invoiceExists("A1")).thenReturn(true);

        final ResponseEntity<Map<String, Boolean>> response = controller("t").regenerate("A1", "t", request);

        assertEquals(Map.of("regenerated", false), response.getBody());
        verify(email, never()).sendPaymentInvoice(any(), any(), any(), any(), any(), any());
    }

    @Test
    void regeneratesAMissingInvoice() {
        when(email.sendPaymentInvoice(any(), any(), any(), any(), any(), any())).thenReturn(true);

        final ResponseEntity<Map<String, Boolean>> response = controller("t").regenerate("A1", "t", request);

        assertEquals(Map.of("regenerated", true), response.getBody());
        verify(email).sendPaymentInvoice("ann@example.com", "Ann Lee", new BigDecimal("10.00"),
                "A1", PaymentMethod.VISA, request.paymentDate());
    }

    @Test
    void reportsBadGatewayWhenRegenerationFails() {
        when(email.sendPaymentInvoice(any(), any(), any(), any(), any(), any())).thenReturn(false);

        assertEquals(HttpStatus.BAD_GATEWAY, controller("t").regenerate("A1", "t", request).getStatusCode());
    }
}
