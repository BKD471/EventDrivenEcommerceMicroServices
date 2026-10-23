package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.configs.s3.S3Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3InvoiceExistsTest {

    private final S3Client client = mock(S3Client.class);
    private final S3Properties properties = mock(S3Properties.class);
    private final S3ServiceImpl service = new S3ServiceImpl(properties, client, mock(S3Presigner.class));

    @Test
    void trueWhenObjectIsPresent() {
        when(properties.bucketName()).thenReturn("b");

        assertTrue(service.invoiceExists("A1"));

        final ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(client).headObject(captor.capture());
        assertEquals("invoices/A1.pdf", captor.getValue().key());
        assertEquals("b", captor.getValue().bucket());
    }

    @Test
    void falseWhenObjectIsMissing() {
        when(properties.bucketName()).thenReturn("b");
        when(client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertFalse(service.invoiceExists("A1"));
    }

    @Test
    void rejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> service.invoiceExists(" "));
    }
}
