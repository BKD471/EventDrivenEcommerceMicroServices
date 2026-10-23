package com.forsaken.ecommerce.notification.controller;

import com.forsaken.ecommerce.notification.service.IEmailService;
import com.forsaken.ecommerce.notification.service.IS3Service;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Internal endpoints used by the payment service's invoice backfill job. They are not routed through the gateway.
 * Regeneration sends an email, so it is disabled unless {@code invoice.backfill.token} is configured and the caller
 * presents the same value in {@code X-Internal-Token}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final java.util.regex.Pattern REFERENCE = java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,100}");

    private final IS3Service s3Service;
    private final IEmailService emailService;
    private final String token;

    public InvoiceController(
            final IS3Service s3Service,
            final IEmailService emailService,
            @Value("${invoice.backfill.token:}") final String token
    ) {
        this.s3Service = s3Service;
        this.emailService = emailService;
        this.token = token;
    }

    @GetMapping("/{orderReference}")
    public ResponseEntity<Map<String, Boolean>> exists(
            @PathVariable final String orderReference,
            @RequestHeader(value = "X-Internal-Token", required = false) final String presented
    ) {
        if (!authorized(presented)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!REFERENCE.matcher(orderReference).matches()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(Map.of("exists", s3Service.invoiceExists(orderReference)));
    }

    @PostMapping("/{orderReference}/regenerate")
    public ResponseEntity<Map<String, Boolean>> regenerate(
            @PathVariable final String orderReference,
            @RequestHeader(value = "X-Internal-Token", required = false) final String presented,
            @Valid @RequestBody final InvoiceRegenerationRequest request
    ) {
        if (!authorized(presented)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!REFERENCE.matcher(orderReference).matches()) {
            return ResponseEntity.badRequest().build();
        }
        if (s3Service.invoiceExists(orderReference)) {
            return ResponseEntity.ok(Map.of("regenerated", false));
        }
        final boolean sent = emailService.sendPaymentInvoice(
                request.customerEmail(), request.customerName(), request.amount(),
                orderReference, request.paymentMethod(), request.paymentDate());
        if (!sent) {
            log.warn("Invoice regeneration failed for orderReference={}", orderReference);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("regenerated", false));
        }
        return ResponseEntity.ok(Map.of("regenerated", true));
    }

    private boolean authorized(final String presented) {
        if (token.isBlank() || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
