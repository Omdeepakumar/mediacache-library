package com.media.cache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Media Cache Helper - Production-ready Android library for caching images and videos
 *
 * Features:
 * - Two-tier caching (Memory LRU + Disk cache)
 * - Callbacks ALWAYS delivered on Main Thread (using Handler)
 * - Duplicate URL handling (queues callbacks for same URL)
 * - Progress tracking during download
 * - Context-based initialization (proper Android cache directory)
 * - Java 7 compatible (no lambdas, no streams, no try-with-resources)
 *
 * Usage:
 *
 * // 1. Initialize once in Application class
 * MediaCacheHelper.init(context);
 *
 * // 2. Get instance anywhere
 * MediaCacheHelper cache = MediaCacheHelper.getInstance();
 *
 * // 3. Configure (can be called anytime - does NOT reset cache)
 * cache.setMemoryCacheSize(100);
 * cache.setDiskCacheSize(200 * 1024 * 1024);
 *
 * // 4. Load media
 * cache.loadMedia(url, MediaType.IMAGE, new CacheCallback.SimpleCallback() {
 *     @Override
 *     public void onSuccess(String url, byte[] data, boolean fromCache) {
 *         // ALWAYS called on main thread - safe to update UI
 *         Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
 *         imageView.setImageBitmap(bitmap);
 *     }
 *
 *     @Override
 *     public void onError(String url, String error) {
 *         // Handle error - ALWAYS on main thread
 *         imageView.setImageResource(R.drawable.error_placeholder);
 *     }
 * });
 */
public class MediaCacheHelper {

    // Singleton instance
    private static volatile MediaCacheHelper instance;

    // Android Context - needed for cache directory
    private Context context;

    // Handler for main thread callback delivery
    private Handler mainHandler;

    // Cache configuration - defaults
    private int memoryCacheSize = 50;
    private long memoryCacheMaxAge = 30 * 60 * 1000L;  // 30 minutes
    private long diskCacheSize = 100 * 1024 * 1024L;     // 100 MB
    private long diskCacheMaxAge = 7 * 24 * 60 * 60 * 1000L;  // 7 days
    private String diskCacheDirName = "media_cache";

    // Caches - initialized lazily
    private MemoryCache memoryCache;
    private DiskCache diskCache;

    // Thread pool for async operations
    private ExecutorService executor;

    // Track loading in progress + pending callbacks for duplicate URLs
    // Map<URL, List<CacheCallback>> - multiple callbacks for same URL
    private final Map<String, List<CacheCallback>> pendingCallbacks;
    private final Map<String, byte[]> loadingResults;

    // Lock for thread safety
    private final Object lock = new Object();

    // Configuration flags
    private boolean diskCacheEnabled = true;
    private boolean memoryCacheEnabled = true;
    private int connectionTimeout = 15000;  // 15 seconds
    private int readTimeout = 30000;          // 30 seconds

    // Initialization flag
    private boolean initialized = false;

    /**
     * Private constructor for singleton
     */
    private MediaCacheHelper() {
        pendingCallbacks = new HashMap<String, List<CacheCallback>>();
        loadingResults = new HashMap<String, byte[]>();
    }

    /**
     * Initialize the cache helper - MUST be called once in Application class
     * @param context Android Context (Application context recommended)
     */
    public static void init(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }

        // Get application context to avoid memory leaks
        final Context appContext = context.getApplicationContext();

        if (instance == null) {
            synchronized (MediaCacheHelper.class) {
                if (instance == null) {
                    instance = new MediaCacheHelper();
                    instance.context = appContext;
                    instance.mainHandler = new Handler(Looper.getMainLooper());
                    instance.initializeCaches();
                    instance.initialized = true;
                }
            }
        }
    }

    /**
     * Initialize caches - called once during init()
     */
    private void initializeCaches() {
        if (context == null) {
            throw new IllegalStateException("Context not set. Call init(context) first.");
        }

        memoryCache = new MemoryCache(memoryCacheSize, memoryCacheMaxAge);
        diskCache = new DiskCache(context, diskCacheDirName, diskCacheSize, diskCacheMaxAge);
        executor = Executors.newFixedThreadPool(4);
    }

    /**
     * Get singleton instance
     * @throws IllegalStateException if not initialized
     */
    public static MediaCacheHelper getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MediaCacheHelper not initialized. Call init(context) first.");
        }
        return instance;
    }

    /**
     * Check if initialized
     */
    public static boolean isInitialized() {
        return instance != null && instance.initialized;
    }

    // ========== CONFIGURATION METHODS ==========
    // These methods update config WITHOUT recreating cache objects
    // Existing cached data is preserved!

    /**
     * Set memory cache size (number of entries)
     * Does NOT clear existing cache
     */
    public void setMemoryCacheSize(int maxEntries) {
        this.memoryCacheSize = maxEntries;
        if (memoryCache != null) {
            memoryCache.setMaxCapacity(maxEntries);
        }
    }

    /**
     * Set memory cache max age in milliseconds
     * Does NOT clear existing cache
     */
    public void setMemoryCacheMaxAge(long maxAgeMillis) {
        this.memoryCacheMaxAge = maxAgeMillis;
        if (memoryCache != null) {
            memoryCache.setMaxAge(maxAgeMillis);
        }
    }

    /**
     * Set disk cache size in bytes
     * Does NOT clear existing cache
     */
    public void setDiskCacheSize(long maxSizeBytes) {
        this.diskCacheSize = maxSizeBytes;
        if (diskCache != null) {
            diskCache.setMaxSize(maxSizeBytes);
        }
    }

    /**
     * Set disk cache max age in milliseconds
     * Does NOT clear existing cache
     */
    public void setDiskCacheMaxAge(long maxAgeMillis) {
        this.diskCacheMaxAge = maxAgeMillis;
        if (diskCache != null) {
            diskCache.setMaxAge(maxAgeMillis);
        }
    }

    /**
     * Set disk cache directory name
     * Note: Changing directory after init will not move existing cache
     */
    public void setDiskCacheDirectory(String dirName) {
        this.diskCacheDirName = dirName;
        // Don't recreate cache - this is just for info
    }

    /**
     * Enable/disable disk cache
     */
    public void setDiskCacheEnabled(boolean enabled) {
        this.diskCacheEnabled = enabled;
    }

    /**
     * Enable/disable memory cache
     */
    public void setMemoryCacheEnabled(boolean enabled) {
        this.memoryCacheEnabled = enabled;
    }

    /**
     * Set connection timeout in milliseconds
     */
    public void setConnectionTimeout(int timeoutMillis) {
        this.connectionTimeout = timeoutMillis;
    }

    /**
     * Set read timeout in milliseconds
     */
    public void setReadTimeout(int timeoutMillis) {
        this.readTimeout = timeoutMillis;
    }

    // ========== LOADING METHODS ==========

    /**
     * Load media asynchronously
     * Checks memory cache -> disk cache -> downloads from network
     *
     * CRITICAL: Callback is ALWAYS delivered on Main Thread
     *
     * @param url Media URL
     * @param mediaType IMAGE or VIDEO
     * @param callback Callback for results (always called on main thread)
     */
    public void loadMedia(final String url, final MediaType mediaType, final CacheCallback callback) {
        if (url == null || url.isEmpty()) {
            notifyError(callback, url, "URL is null or empty");
            return;
        }

        if (callback == null) {
            return;
        }

        // Synchronous check for cached data first (fast path)
        // Step 1: Check memory cache
        if (memoryCacheEnabled && memoryCache != null) {
            CacheEntry entry = memoryCache.get(url);
            if (entry != null) {
                final byte[] data = entry.getData();
                notifySuccess(callback, url, data, true);
                return;
            }
        }

        // Step 2: Check disk cache (synchronous)
        if (diskCacheEnabled && diskCache != null) {
            CacheEntry entry = diskCache.get(url);
            if (entry != null) {
                final byte[] data = entry.getData();
                // Promote to memory cache
                if (memoryCacheEnabled && memoryCache != null && data != null) {
                    CacheEntry memEntry = new CacheEntry(url, mediaType, data, entry.getContentType());
                    memoryCache.put(memEntry);
                }
                notifySuccess(callback, url, data, true);
                return;
            }
        }

        // Need to download from network
        // Handle duplicate URL: add callback to pending list
        synchronized (lock) {
            List<CacheCallback> callbacks = pendingCallbacks.get(url);
            if (callbacks == null) {
                // First request for this URL - start loading
                callbacks = new ArrayList<CacheCallback>();
                callbacks.add(callback);
                pendingCallbacks.put(url, callbacks);

                // Start download
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        downloadAndNotify(url, mediaType);
                    }
                });
            } else {
                // Duplicate request - add to pending list
                callbacks.add(callback);
            }
        }
    }

    /**
     * Download from network and notify all pending callbacks
     */
    private void downloadAndNotify(String url, MediaType mediaType) {
        byte[] data = null;
        String error = null;
        boolean fromCache = false;

        // Download from network
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;

        try {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("User-Agent", "MediaCacheHelper/1.0");
            connection.setRequestProperty("Accept", "*/*");
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            // Handle redirects manually
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                String redirectUrl = connection.getHeaderField("Location");
                if (redirectUrl != null) {
                    connection.disconnect();
                    connection = (HttpURLConnection) new URL(redirectUrl).openConnection();
                    connection.setConnectTimeout(connectionTimeout);
                    connection.setReadTimeout(readTimeout);
                    connection.setRequestProperty("User-Agent", "MediaCacheHelper/1.0");
                    responseCode = connection.getResponseCode();
                }
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                error = "HTTP error: " + responseCode;
            } else {
                // Get content info
                String contentType = connection.getContentType();
                long contentLength = connection.getContentLength();
                if (contentLength <= 0) {
                    contentLength = -1;
                }

                // Read data
                inputStream = connection.getInputStream();
                outputStream = new ByteArrayOutputStream();

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    // Report progress to all pending callbacks
                    final long progressTotal = totalRead;
                    final long progressContent = contentLength;
                    final long finalTotal = totalRead;

                    synchronized (lock) {
                        List<CacheCallback> callbacks = pendingCallbacks.get(url);
                        if (callbacks != null) {
                            for (CacheCallback cb : callbacks) {
                                notifyProgress(cb, url, finalTotal, contentLength);
                            }
                        }
                    }
                }

                data = outputStream.toByteArray();

                // Detect media type from content
                MediaType detectedType = mediaType;
                if (contentType != null) {
                    if (contentType.startsWith("video/")) {
                        detectedType = MediaType.VIDEO;
                    } else if (contentType.startsWith("image/")) {
                        detectedType = MediaType.IMAGE;
                    }
                }

                // Create cache entry
                CacheEntry downloaded = new CacheEntry(url, detectedType, data, contentType);

                // Save to caches
                if (memoryCacheEnabled && memoryCache != null) {
                    memoryCache.put(downloaded);
                }
                if (diskCacheEnabled && diskCache != null) {
                    diskCache.put(downloaded);
                }
            }

        } catch (Exception e) {
            error = "Download failed: " + e.getMessage();
        } finally {
            closeStream(inputStream);
            closeStream(outputStream);
            if (connection != null) {
                connection.disconnect();
            }
        }

        // Notify all pending callbacks on main thread
        synchronized (lock) {
            List<CacheCallback> callbacks = pendingCallbacks.remove(url);

            if (callbacks != null) {
                for (CacheCallback cb : callbacks) {
                    if (error != null) {
                        notifyError(cb, url, error);
                    } else if (data != null) {
                        notifySuccess(cb, url, data, false);
                    }
                }
            }
        }
    }

    /**
     * Load media synchronously (blocking)
     */
    public CacheEntry loadMediaSync(String url, MediaType mediaType) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Check memory cache
        if (memoryCacheEnabled && memoryCache != null) {
            CacheEntry entry = memoryCache.get(url);
            if (entry != null) {
                return entry;
            }
        }

        // Check disk cache
        if (diskCacheEnabled && diskCache != null) {
            CacheEntry entry = diskCache.get(url);
            if (entry != null) {
                // Promote to memory
                if (memoryCacheEnabled && memoryCache != null) {
                    memoryCache.put(entry);
                }
                return entry;
            }
        }

        // Download synchronously (expensive - avoid on main thread)
        return downloadSync(url, mediaType);
    }

    /**
     * Download synchronously (internal)
     */
    private CacheEntry downloadSync(String url, MediaType mediaType) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;

        try {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(readTimeout);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                String contentType = connection.getContentType();
                inputStream = connection.getInputStream();
                outputStream = new ByteArrayOutputStream();

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                byte[] data = outputStream.toByteArray();
                CacheEntry entry = new CacheEntry(url, mediaType, data, contentType);

                // Cache it
                if (memoryCacheEnabled && memoryCache != null) {
                    memoryCache.put(entry);
                }
                if (diskCacheEnabled && diskCache != null) {
                    diskCache.put(entry);
                }

                return entry;
            }
        } catch (Exception e) {
            // Ignore
        } finally {
            closeStream(inputStream);
            closeStream(outputStream);
            if (connection != null) {
                connection.disconnect();
            }
        }

        return null;
    }

    // ========== CALLBACK NOTIFICATION (MAIN THREAD) ==========
    // CRITICAL: All callbacks delivered on main thread via Handler

    /**
     * Notify success - ALWAYS on main thread
     */
    private void notifySuccess(final CacheCallback callback, final String url,
                               final byte[] data, final boolean fromCache) {
        if (callback == null) {
            return;
        }

        // Post to main thread
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(url, data, fromCache);
            }
        });
    }

    /**
     * Notify error - ALWAYS on main thread
     */
    private void notifyError(final CacheCallback callback, final String url, final String error) {
        if (callback == null) {
            return;
        }

        // Post to main thread
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(url, error);
            }
        });
    }

    /**
     * Notify progress - ALWAYS on main thread
     */
    private void notifyProgress(final CacheCallback callback, final String url,
                                 final long bytesLoaded, final long totalBytes) {
        if (callback == null) {
            return;
        }

        // Post to main thread
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onProgress(url, bytesLoaded, totalBytes);
            }
        });
    }

    // ========== CACHE MANAGEMENT ==========

    /**
     * Clear all caches (memory + disk)
     */
    public void clearAllCaches() {
        if (memoryCache != null) {
            memoryCache.clear();
        }
        if (diskCache != null) {
            diskCache.clear();
        }
    }

    /**
     * Clear only memory cache
     */
    public void clearMemoryCache() {
        if (memoryCache != null) {
            memoryCache.clear();
        }
    }

    /**
     * Clear only disk cache
     */
    public void clearDiskCache() {
        if (diskCache != null) {
            diskCache.clear();
        }
    }

    /**
     * Remove specific URL from all caches
     */
    public boolean remove(String url) {
        boolean removed = false;

        if (memoryCache != null) {
            CacheEntry entry = memoryCache.remove(url);
            removed = entry != null;
        }

        if (diskCache != null) {
            boolean diskRemoved = diskCache.remove(url);
            removed = removed || diskRemoved;
        }

        return removed;
    }

    /**
     * Check if URL is cached (in any cache)
     */
    public boolean isCached(String url) {
        if (memoryCacheEnabled && memoryCache != null && memoryCache.contains(url)) {
            return true;
        }
        if (diskCacheEnabled && diskCache != null && diskCache.contains(url)) {
            return true;
        }
        return false;
    }

    /**
     * Get data for cached URL synchronously
     */
    public byte[] getCachedData(String url) {
        // Try memory first
        if (memoryCacheEnabled && memoryCache != null) {
            CacheEntry entry = memoryCache.get(url);
            if (entry != null) {
                return entry.getData();
            }
        }

        // Try disk
        if (diskCacheEnabled && diskCache != null) {
            CacheEntry entry = diskCache.get(url);
            if (entry != null) {
                return entry.getData();
            }
        }

        return null;
    }

    /**
     * Remove expired entries from all caches
     */
    public int removeExpired() {
        int removed = 0;

        if (memoryCache != null) {
            removed += memoryCache.removeExpired();
        }
        if (diskCache != null) {
            removed += diskCache.removeExpired();
        }

        return removed;
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        MemoryCache.CacheStats memoryStats = memoryCache != null ? memoryCache.getStats() : null;
        long diskSize = diskCache != null ? diskCache.getSize() : 0;
        int diskEntries = diskCache != null ? diskCache.getEntryCount() : 0;

        return new CacheStats(
                memoryStats != null ? memoryStats.entryCount : 0,
                memoryCacheSize,
                memoryStats != null ? memoryStats.currentSizeBytes : 0,
                diskEntries,
                diskCacheSize,
                diskSize
        );
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    /**
     * Close stream safely
     */
    private void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    // ========== CACHE STATS ==========

    /**
     * Cache statistics holder
     */
    public static class CacheStats {
        public final int memoryEntryCount;
        public final int memoryMaxEntries;
        public final long memorySizeBytes;
        public final int diskEntryCount;
        public final long diskMaxSizeBytes;
        public final long diskSizeBytes;

        public CacheStats(int memoryEntryCount, int memoryMaxEntries, long memorySizeBytes,
                          int diskEntryCount, long diskMaxSizeBytes, long diskSizeBytes) {
            this.memoryEntryCount = memoryEntryCount;
            this.memoryMaxEntries = memoryMaxEntries;
            this.memorySizeBytes = memorySizeBytes;
            this.diskEntryCount = diskEntryCount;
            this.diskMaxSizeBytes = diskMaxSizeBytes;
            this.diskSizeBytes = diskSizeBytes;
        }

        @Override
        public String toString() {
            return "CacheStats{\n" +
                    "  Memory: " + memoryEntryCount + "/" + memoryMaxEntries + " entries (" +
                    formatSize(memorySizeBytes) + ")\n" +
                    "  Disk: " + diskEntryCount + " entries (" +
                    formatSize(diskSizeBytes) + " / " + formatSize(diskMaxSizeBytes) + ")\n" +
                    "}";
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
