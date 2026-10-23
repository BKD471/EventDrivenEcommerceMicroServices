package com.forsaken.ecommerce.product.batch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Object storage used by the catalog import and export jobs. */
public interface ProductFileStorage {

    record StoredFile(String key, Instant lastModified) {
    }

    List<StoredFile> list(String prefix);

    InputStream open(String key) throws IOException;

    void upload(String key, Path file);

    void move(String fromKey, String toKey);
}
