package com.media.cache;

import java.io.InputStream;

/**
 * Callback interface for async cache operations
 * Java 7 compatible - no lambdas, uses anonymous inner classes
 */
public interface CacheCallback {

    /**
     * Called when media is successfully loaded from cache
     * @param url The original URL
     * @param data The cached data (byte array for video, InputStream for image)
     * @param fromCache Whether data was loaded from cache (true) or downloaded (false)
     */
    void onSuccess(String url, byte[] data, boolean fromCache);

    /**
     * Called when media loading fails
     * @param url The original URL
     * @param error Error message
     */
    void onError(String url, String error);

    /**
     * Called during download progress
     * @param url The URL being downloaded
     * @param bytesLoaded Bytes downloaded so far
     * @param totalBytes Total bytes to download (-1 if unknown)
     */
    void onProgress(String url, long bytesLoaded, long totalBytes);

    /**
     * Simple callback with only success/failure
     * For use with lambdas disabled environments
     */
    public static abstract class SimpleCallback implements CacheCallback {

        @Override
        public void onProgress(String url, long bytesLoaded, long totalBytes) {
            // Default empty implementation
        }
    }
}
