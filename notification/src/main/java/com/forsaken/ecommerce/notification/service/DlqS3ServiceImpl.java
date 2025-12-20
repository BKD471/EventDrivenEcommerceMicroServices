package com.forsaken.ecommerce.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.forsaken.ecommerce.notification.configs.s3.DlqS3Properties;
import com.forsaken.ecommerce.notification.models.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.forsaken.ecommerce.notification.mapper.AvroJsonConverter.avroToJson;

@Service
@RequiredArgsConstructor
@Slf4j
public class DlqS3ServiceImpl implements IDlqS3Service {

    private final S3Client s3Client;
    private final DlqS3Properties properties;
    // Configured for Avro payloads that may contain Java time types (e.g. Instant)
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void storeToS3(
            final ConsumerRecord<String, ?> record,
            final EventType eventType
    ) throws IOException {
        log.info("📦 Storing DLQ event into S3 for topic={}, offset={}", record.topic(), record.offset());

        final String prefix = switch (eventType) {
            case PAYMENT -> properties.paymentPrefix();
            case ORDER -> properties.orderPrefix();
        };
        final String jsonPayload;
        final Object value = record.value();
        if (value instanceof org.apache.avro.specific.SpecificRecordBase) {
            jsonPayload = avroToJson((org.apache.avro.specific.SpecificRecordBase) value);
        } else {
            jsonPayload = mapper.writeValueAsString(value);
        }
        final Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("partition", record.partition());
        wrapper.put("offset", record.offset());
        wrapper.put("topic", record.topic());
        wrapper.put("key", record.key());
        wrapper.put("timestamp", record.timestamp());
        wrapper.put("valueJson", jsonPayload);

        final String finalJson = mapper.writeValueAsString(wrapper);
        final String s3Key = prefix + "/" + UUID.randomUUID() + ".json";
        final PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(s3Key)
                .contentType("application/json")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromString(finalJson));
        log.info("Stored DLQ event at s3://{}/{}", properties.bucket(), s3Key);
    }

    @Override
    public List<String> listKeys(final String prefix) {
        log.info("Listing DLQ event into S3 for prefix={}", prefix);
        final List<String> keysList = new ArrayList<>();
        final ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(properties.bucket())
                .prefix(prefix)
                .build();
        final ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);
        if (listObjectsV2Response.contents() == null) return keysList;
        listObjectsV2Response.contents().forEach(obj -> keysList.add(obj.key()));
        log.info("Found DLQ event into S3 for prefix={}", prefix);
        return keysList;
    }

    @Override
    public String load(final String key) {
        log.info("Loading DLQ file from s3://{}/{}", properties.bucket(), key);
        final GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        return s3Client.getObjectAsBytes(getObjectRequest)
                .asString(StandardCharsets.UTF_8);
    }

    @Override
    public void delete(final String key) {
        log.info("Deleting DLQ record from S3: {}", key);
        final DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }
}
