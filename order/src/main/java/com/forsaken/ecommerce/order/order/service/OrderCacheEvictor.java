package com.forsaken.ecommerce.order.order.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

/**
 * Central invalidation of order caches, called after every committed saga state change,
 * including failed creations and scheduled recovery.
 */
@Component
public class OrderCacheEvictor {

    @CacheEvict(value = {"orders", "orderById", "ordersByCustomer"}, allEntries = true)
    public void evictAll() {
        // eviction is performed by the cache annotation
    }
}
