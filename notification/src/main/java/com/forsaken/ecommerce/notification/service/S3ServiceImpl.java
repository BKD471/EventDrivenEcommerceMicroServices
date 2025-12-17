package com.forsaken.ecommerce.notification.service;

import com.forsaken.ecommerce.notification.configs.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImpl implements IS3Service {

    private final S3Properties s3Properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Override
    public String uploadInvoice(
            final byte[] pdfBytes,
            final String invoiceId
    ) {
        log.info("Received request to upload invoice with id {}", invoiceId);
        final String key = "invoices/" + invoiceId + ".pdf";

        final PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .contentType("application/pdf")
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(pdfBytes));
        log.info("Uploaded invoice with id {}", invoiceId);
        return key;
    }

    @Override
    public URL generatePresignedUrl(final String key) {
        log.info("Received request to generate presigned url for key {}", key);
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.expiration()))
                .getObjectRequest(getRequest)
                .build();
        log.info("Generated presigned url for key {}", key);
        return s3Presigner.presignGetObject(presignRequest).url();
    }
}
