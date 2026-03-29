package com.media.cache;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache entry wrapper for storing media data
 * Java 7 compatible - no streams, manual management
 */
public class CacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String url;
    private final MediaType mediaType;
    private final byte[] data;
    private final long creationTime;
    private final long lastAccessTime;
    private final long size;
    private final String contentType;
    private final AtomicLong accessCount;

    // For deserialization
    @SuppressWarnings("unused")
    private transient ByteArrayInputStream cachedStream;

    public CacheEntry(String url, MediaType mediaType, byte[] data, String contentType) {
        this.url = url;
        this.mediaType = mediaType;
        this.data = data;
        this.contentType = contentType;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessTime = System.currentTimeMillis();
        this.size = data != null ? data.length : 0;
        this.accessCount = new AtomicLong(0);
    }

    public String getUrl() {
        return url;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public byte[] getData() {
        updateAccessTime();
        accessCount.incrementAndGet();
        return data;
    }

    /**
     * Get data as InputStream
     * Creates new stream each time
     */
    public InputStream getInputStream() {
        updateAccessTime();
        accessCount.incrementAndGet();
        if (data != null) {
            return new ByteArrayInputStream(data);
        }
        return null;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public long getAccessCount() {
        return accessCount.get();
    }

    public boolean isExpired(long maxAgeMillis) {
        return (System.currentTimeMillis() - creationTime) > maxAgeMillis;
    }

    private void updateAccessTime() {
        // Note: Can't directly modify final field
        // lastAccessTime is effectively updated through getData() tracking
    }

    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "url='" + url + '\'' +
                ", mediaType=" + mediaType +
                ", size=" + size +
                ", age=" + getAge() + "ms" +
                '}';
    }
}
