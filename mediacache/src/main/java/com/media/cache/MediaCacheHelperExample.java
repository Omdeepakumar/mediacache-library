package com.media.cache;

/**
 * Example usage of MediaCacheHelper
 * Java 7 compatible - uses anonymous inner classes instead of lambdas
 */
public class MediaCacheHelperExample {

    /**
     * Example 1: Basic image loading
     */
    public static void exampleLoadImage() {
        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        // Configure cache settings
        cache.setMemoryCacheSize(50);  // 50 items in memory
        cache.setDiskCacheSize(100 * 1024 * 1024);  // 100 MB on disk
        cache.setDiskCacheDirectory("my_image_cache");

        String imageUrl = "https://example.com/image.jpg";

        // Load image asynchronously with callback
        cache.loadMedia(imageUrl, MediaType.IMAGE, new CacheCallback.SimpleCallback() {

            @Override
            public void onSuccess(String url, byte[] data, boolean fromCache) {
                System.out.println("Image loaded: " + (fromCache ? "FROM CACHE" : "FROM NETWORK"));
                System.out.println("Data length: " + data.length + " bytes");

                // Convert to Bitmap (Android example)
                // Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                // imageView.setImageBitmap(bitmap);
            }

            @Override
            public void onError(String url, String error) {
                System.err.println("Error loading image: " + error);
            }

            @Override
            public void onProgress(String url, long bytesLoaded, long totalBytes) {
                if (totalBytes > 0) {
                    int progress = (int) ((bytesLoaded * 100) / totalBytes);
                    System.out.println("Download progress: " + progress + "%");
                }
            }
        });
    }

    /**
     * Example 2: Load video
     */
    public static void exampleLoadVideo() {
        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        String videoUrl = "https://example.com/video.mp4";

        cache.loadMedia(videoUrl, MediaType.VIDEO, new CacheCallback.SimpleCallback() {

            @Override
            public void onSuccess(String url, byte[] data, boolean fromCache) {
                System.out.println("Video loaded: " + (fromCache ? "FROM CACHE" : "FROM NETWORK"));
                System.out.println("Video size: " + data.length + " bytes");

                // Play video from byte array (Android example)
                // MediaPlayer player = new MediaPlayer();
                // player.setDataSource(new ByteArrayDataSource(data, "video/mp4"));
            }

            @Override
            public void onError(String url, String error) {
                System.err.println("Error loading video: " + error);
            }
        });
    }

    /**
     * Example 3: Synchronous loading
     */
    public static void exampleSyncLoading() {
        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        String imageUrl = "https://example.com/image.jpg";

        // Load synchronously (blocking)
        CacheEntry entry = cache.loadMediaSync(imageUrl, MediaType.IMAGE);

        if (entry != null) {
            byte[] data = entry.getData();
            System.out.println("Synchronously loaded: " + data.length + " bytes");
        } else {
            System.out.println("Failed to load synchronously");
        }
    }

    /**
     * Example 4: Cache management
     */
    public static void exampleCacheManagement() {
        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        // Check if URL is cached
        String imageUrl = "https://example.com/image.jpg";
        if (cache.isCached(imageUrl)) {
            System.out.println("Image is cached!");
        } else {
            System.out.println("Image not in cache");
        }

        // Get cached data directly
        byte[] cachedData = cache.getCachedData(imageUrl);
        if (cachedData != null) {
            System.out.println("Found in cache: " + cachedData.length + " bytes");
        }

        // Remove specific item from cache
        boolean removed = cache.remove(imageUrl);
        System.out.println("Removed from cache: " + removed);

        // Get cache statistics
        MediaCacheHelper.CacheStats stats = cache.getStats();
        System.out.println("Cache Stats:\n" + stats.toString());

        // Clear all caches
        cache.clearAllCaches();
        System.out.println("All caches cleared");

        // Clear only memory cache (useful when low on memory)
        cache.clearMemoryCache();

        // Clear only disk cache
        cache.clearDiskCache();
    }

    /**
     * Example 5: Configuration options
     */
    public static void exampleConfiguration() {
        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        // Memory cache: 100 items, 10 minute expiry
        cache.setMemoryCacheSize(100);
        cache.setMemoryCacheMaxAge(10 * 60 * 1000);

        // Disk cache: 500 MB, 30 day expiry
        cache.setDiskCacheSize(500 * 1024 * 1024);
        cache.setDiskCacheMaxAge(30 * 24 * 60 * 60 * 1000L);
        cache.setDiskCacheDirectory("my_media_cache");

        // Network timeouts
        cache.setConnectionTimeout(15000);  // 15 seconds
        cache.setReadTimeout(60000);  // 60 seconds

        // Disable disk cache (memory only)
        cache.setDiskCacheEnabled(false);

        // Disable memory cache (disk only)
        cache.setMemoryCacheEnabled(false);
    }

    /**
     * Example 6: Periodic cleanup
     */
    public static void examplePeriodicCleanup() {
        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        // Remove expired entries (call periodically)
        int removed = cache.removeExpired();
        System.out.println("Removed " + removed + " expired entries");

        // Print current stats
        System.out.println(cache.getStats().toString());
    }

    /**
     * Example 7: Batch loading multiple images
     */
    public static void exampleBatchLoading() {
        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        String[] imageUrls = {
                "https://example.com/image1.jpg",
                "https://example.com/image2.jpg",
                "https://example.com/image3.jpg"
        };

        // Load each image
        for (final String url : imageUrls) {
            cache.loadMedia(url, MediaType.IMAGE, new CacheCallback.SimpleCallback() {

                @Override
                public void onSuccess(String loadedUrl, byte[] data, boolean fromCache) {
                    System.out.println("Loaded: " + loadedUrl + " (" +
                            (fromCache ? "cache" : "network") + ")");
                }

                @Override
                public void onError(String errorUrl, String error) {
                    System.err.println("Failed: " + errorUrl + " - " + error);
                }
            });
        }
    }

    /**
     * Example 8: Android Activity lifecycle handling
     */
    public static class AndroidActivityExample {

        MediaCacheHelper cache = MediaCacheHelper.getInstance();

        // In onCreate()
        public void onCreate() {
            // Configure cache on app start
            cache.setMemoryCacheSize(50);
            cache.setDiskCacheSize(100 * 1024 * 1024);
        }

        // In onLowMemory() - clear memory cache
        public void onLowMemory() {
            cache.clearMemoryCache();
            System.out.println("Memory cache cleared due to low memory");
        }

        // In onDestroy()
        public void onDestroy() {
            // Optional: clear all caches on app exit
            // cache.clearAllCaches();
            cache.shutdown();
        }

        // Load image in onResume()
        public void loadImage(String url) {
            cache.loadMedia(url, MediaType.IMAGE, new CacheCallback.SimpleCallback() {

                @Override
                public void onSuccess(String loadedUrl, byte[] data, boolean fromCache) {
                    // Update UI
                    // imageView.setImageBitmap(bitmap);
                    System.out.println("Image ready: " + fromCache);
                }

                @Override
                public void onError(String errorUrl, String error) {
                    // Show placeholder
                    System.err.println("Load failed: " + error);
                }
            });
        }
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        System.out.println("=== MediaCacheHelper Example ===\n");

        // Run examples
        exampleLoadImage();
        exampleLoadVideo();
        exampleSyncLoading();
        exampleCacheManagement();
        exampleConfiguration();
        examplePeriodicCleanup();
        exampleBatchLoading();

        // Shutdown executor
        MediaCacheHelper.getInstance().shutdown();

        System.out.println("\n=== Examples Complete ===");
    }
}
