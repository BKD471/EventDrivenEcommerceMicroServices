package com.forsaken.ecommerce.product.batch;

import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

/** Lets a flat-file reader (re)open an object in storage, which is what makes a restarted import possible. */
class S3ObjectResource extends AbstractResource {

    private final ProductFileStorage storage;
    private final String key;

    S3ObjectResource(final ProductFileStorage storage, final String key) {
        this.storage = storage;
        this.key = key;
    }

    @Override
    public String getDescription() {
        return "storage object [" + key + "]";
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return storage.open(key);
    }
}
