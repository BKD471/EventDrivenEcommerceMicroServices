package com.forsaken.ecommerce.product.batch;

import com.forsaken.ecommerce.product.configs.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
public class S3ProductFileStorage implements ProductFileStorage {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Override
    public List<StoredFile> list(final String prefix) {
        return s3Client.listObjectsV2Paginator(b -> b.bucket(s3Properties.bucketName()).prefix(prefix))
                .contents().stream()
                .filter(object -> !object.key().equals(prefix) && object.size() > 0)
                .map(object -> new StoredFile(object.key(), object.lastModified()))
                .toList();
    }

    @Override
    public InputStream open(final String key) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(s3Properties.bucketName()).key(key).build());
    }

    @Override
    public void upload(final String key, final Path file) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(s3Properties.bucketName()).key(key).contentType("text/csv").build(),
                RequestBody.fromFile(file));
    }

    @Override
    public void move(final String fromKey, final String toKey) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(s3Properties.bucketName()).sourceKey(fromKey)
                .destinationBucket(s3Properties.bucketName()).destinationKey(toKey).build());
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Properties.bucketName()).key(fromKey).build());
    }
}
