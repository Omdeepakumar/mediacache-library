package com.media.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU Memory Cache implementation
 * Java 7 compatible - uses LinkedHashMap with removeEldestEntry pattern
 * Configuration methods update values without recreating cache
 */
public class MemoryCache {

    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private int maxCapacity;
    private final LinkedHashMap<String, CacheEntry> cache;
    private long maxAgeMillis;
    private long currentSize;

    // Lock for thread safety
    private final Object lock = new Object();

    /**
     * Create memory cache with max capacity
     * @param maxCapacity Maximum number of entries
     */
    public MemoryCache(int maxCapacity) {
        this(maxCapacity, Long.MAX_VALUE);
    }

    /**
     * Create memory cache with max capacity and max age
     * @param maxCapacity Maximum number of entries
     * @param maxAgeMillis Maximum age of entries in milliseconds
     */
    public MemoryCache(int maxCapacity, long maxAgeMillis) {
        this.maxCapacity = maxCapacity;
        this.maxAgeMillis = maxAgeMillis;
        this.currentSize = 0;

        // Initialize LinkedHashMap with access order and LRU eviction
        int initialCapacity = Math.min(maxCapacity, 100);
        cache = new LinkedHashMap<String, CacheEntry>(
                initialCapacity,
                DEFAULT_LOAD_FACTOR,
                true // accessOrder = true for LRU
        ) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                // This is called during put(), we handle eviction in put() method
                // for better control and size tracking
                return false;
            }
        };
    }

    /**
     * Update max capacity - does NOT clear existing cache
     * Will evict entries if over new limit
     */
    public void setMaxCapacity(int maxCapacity) {
        synchronized (lock) {
            this.maxCapacity = maxCapacity;
            // Evict if over new limit
            evictToCapacity();
        }
    }

    /**
     * Update max age - does NOT clear existing cache
     */
    public void setMaxAge(long maxAgeMillis) {
        synchronized (lock) {
            this.maxAgeMillis = maxAgeMillis;
        }
    }

    /**
     * Get entry from cache
     * @param url The URL key
     * @return CacheEntry or null if not found/expired
     */
    public CacheEntry get(String url) {
        synchronized (lock) {
            CacheEntry entry = cache.get(url);
            if (entry != null) {
                // Check if expired
                if (entry.isExpired(maxAgeMillis)) {
                    removeLocked(url);
                    return null;
                }
            }
            return entry;
        }
    }

    /**
     * Put entry in cache
     * @param entry The cache entry to store
     */
    public void put(CacheEntry entry) {
        if (entry == null || entry.getData() == null) {
            return;
        }

        synchronized (lock) {
            String url = entry.getUrl();

            // Remove old entry if exists
            CacheEntry oldEntry = cache.remove(url);
            if (oldEntry != null) {
                currentSize -= oldEntry.getSize();
            }

            // Add new entry
            cache.put(url, entry);
            currentSize += entry.getSize();

            // Evict to capacity
            evictToCapacity();
        }
    }

    /**
     * Evict entries until under capacity
     */
    private void evictToCapacity() {
        while (cache.size() > maxCapacity) {
            Map.Entry<String, CacheEntry> eldest = null;
            for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
                eldest = e;
                break;
            }
            if (eldest != null) {
                removeLocked(eldest.getKey());
            } else {
                break;
            }
        }
    }

    /**
     * Remove entry from cache
     * @param url The URL key
     * @return The removed entry or null
     */
    public CacheEntry remove(String url) {
        synchronized (lock) {
            return removeLocked(url);
        }
    }

    /**
     * Internal remove with lock held
     */
    private CacheEntry removeLocked(String url) {
        CacheEntry entry = cache.remove(url);
        if (entry != null) {
            currentSize -= entry.getSize();
        }
        return entry;
    }

    /**
     * Clear all entries
     */
    public void clear() {
        synchronized (lock) {
            cache.clear();
            currentSize = 0;
        }
    }

    /**
     * Check if URL exists in cache
     * @param url The URL to check
     * @return true if cached and not expired
     */
    public boolean contains(String url) {
        synchronized (lock) {
            CacheEntry entry = cache.get(url);
            if (entry != null && !entry.isExpired(maxAgeMillis)) {
                return true;
            }
            return false;
        }
    }

    /**
     * Get number of entries
     */
    public int size() {
        synchronized (lock) {
            return cache.size();
        }
    }

    /**
     * Get current cache size in bytes
     */
    public long getCurrentSize() {
        synchronized (lock) {
            return currentSize;
        }
    }

    /**
     * Get all cached URLs
     */
    public String[] getKeys() {
        synchronized (lock) {
            return cache.keySet().toArray(new String[cache.size()]);
        }
    }

    /**
     * Remove expired entries
     */
    public int removeExpired() {
        synchronized (lock) {
            int removed = 0;
            String[] keys = cache.keySet().toArray(new String[cache.size()]);
            for (String key : keys) {
                CacheEntry entry = cache.get(key);
                if (entry != null && entry.isExpired(maxAgeMillis)) {
                    removeLocked(key);
                    removed++;
                }
            }
            return removed;
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        synchronized (lock) {
            return new CacheStats(
                    cache.size(),
                    maxCapacity,
                    currentSize,
                    maxAgeMillis
            );
        }
    }

    /**
     * Get current max capacity
     */
    public int getMaxCapacity() {
        synchronized (lock) {
            return maxCapacity;
        }
    }

    /**
     * Get current max age
     */
    public long getMaxAge() {
        synchronized (lock) {
            return maxAgeMillis;
        }
    }

    /**
     * Cache statistics holder
     */
    public static class CacheStats {
        public final int entryCount;
        public final int maxCapacity;
        public final long currentSizeBytes;
        public final long maxAgeMillis;

        public CacheStats(int entryCount, int maxCapacity, long currentSizeBytes, long maxAgeMillis) {
            this.entryCount = entryCount;
            this.maxCapacity = maxCapacity;
            this.currentSizeBytes = currentSizeBytes;
            this.maxAgeMillis = maxAgeMillis;
        }

        @Override
        public String toString() {
            return "CacheStats{" +
                    "entries=" + entryCount + "/" + maxCapacity +
                    ", size=" + formatSize(currentSizeBytes) +
                    '}';
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
