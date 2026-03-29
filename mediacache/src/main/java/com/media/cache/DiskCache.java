package com.media.cache;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Disk Cache implementation for persistent Android storage
 * Uses Context.getCacheDir() for proper Android cache directory
 * Java 7 compatible - no try-with-resources, manual stream management
 */
public class DiskCache {

    private File cacheDir;
    private long maxSizeBytes;
    private long maxAgeMillis;
    private final Map<String, DiskEntry> entryIndex;
    private long currentSize;

    // Lock for thread safety
    private final Object lock = new Object();

    /**
     * Create disk cache using Android Context
     * @param context Android Context (will use context.getCacheDir())
     * @param cacheName Subdirectory name for cache organization
     * @param maxSizeBytes Maximum cache size in bytes
     */
    public DiskCache(Context context, String cacheName, long maxSizeBytes) {
        this(context, cacheName, maxSizeBytes, Long.MAX_VALUE);
    }

    /**
     * Create disk cache with all options
     * @param context Android Context
     * @param cacheName Subdirectory name
     * @param maxSizeBytes Maximum cache size in bytes
     * @param maxAgeMillis Maximum age of entries in milliseconds
     */
    public DiskCache(Context context, String cacheName, long maxSizeBytes, long maxAgeMillis) {
        // Use context.getCacheDir() - this is the correct Android way!
        File baseCacheDir = context.getCacheDir();
        this.cacheDir = new File(baseCacheDir, cacheName);
        this.maxSizeBytes = maxSizeBytes;
        this.maxAgeMillis = maxAgeMillis;
        this.entryIndex = new HashMap<String, DiskEntry>();
        this.currentSize = 0;

        // Create cache directory
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        // Scan existing cache files
        scanCache();
    }

    /**
     * Update max size - does NOT clear existing cache
     */
    public void setMaxSize(long maxSizeBytes) {
        synchronized (lock) {
            this.maxSizeBytes = maxSizeBytes;
            // Evict if over new limit
            while (currentSize > this.maxSizeBytes && !entryIndex.isEmpty()) {
                removeOldestLocked();
            }
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
     * Scan cache directory and build index from existing files
     * Reads .meta files to get mediaType and contentType
     */
    private void scanCache() {
        synchronized (lock) {
            if (!cacheDir.exists()) {
                return;
            }

            File[] files = cacheDir.listFiles();
            if (files == null) {
                return;
            }

            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".cache")) {
                    String key = file.getName().replace(".cache", "");
                    long size = file.length();
                    long lastModified = file.lastModified();

                    // Read metadata from .meta file
                    File metaFile = new File(cacheDir, key + ".meta");
                    MediaType mediaType = MediaType.IMAGE;
                    String contentType = "application/octet-stream";
                    long timestamp = lastModified;

                    if (metaFile.exists()) {
                        String[] meta = readMetaFile(metaFile);
                        if (meta != null) {
                            if (meta[0] != null) {
                                mediaType = meta[0].equals("VIDEO") ? MediaType.VIDEO : MediaType.IMAGE;
                            }
                            if (meta[1] != null) {
                                contentType = meta[1];
                            }
                            if (meta[2] != null) {
                                try {
                                    timestamp = Long.parseLong(meta[2]);
                                } catch (NumberFormatException e) {
                                    // Use file lastModified as fallback
                                }
                            }
                        }
                    }

                    DiskEntry entry = new DiskEntry(key, file, metaFile, size, timestamp);
                    entry.mediaType = mediaType;
                    entry.contentType = contentType;
                    entryIndex.put(key, entry);
                    currentSize += size;
                }
            }
        }
    }

    /**
     * Read metadata file - plain text format
     * Line 1: mediaType (IMAGE/VIDEO)
     * Line 2: contentType
     * Line 3: timestamp
     */
    private String[] readMetaFile(File metaFile) {
        FileInputStream fis = null;
        InputStreamReader isr = null;
        BufferedReader reader = null;

        try {
            fis = new FileInputStream(metaFile);
            isr = new InputStreamReader(fis);
            reader = new BufferedReader(isr);

            String mediaType = reader.readLine();
            String contentType = reader.readLine();
            String timestamp = reader.readLine();

            return new String[]{mediaType, contentType, timestamp};

        } catch (IOException e) {
            return null;
        } finally {
            closeStream(reader);
            closeStream(isr);
            closeStream(fis);
        }
    }

    /**
     * Write metadata file
     */
    private boolean writeMetaFile(File metaFile, MediaType mediaType, String contentType, long timestamp) {
        FileOutputStream fos = null;
        OutputStreamWriter osw = null;
        BufferedWriter writer = null;

        try {
            fos = new FileOutputStream(metaFile);
            osw = new OutputStreamWriter(fos);
            writer = new BufferedWriter(osw);

            writer.write(mediaType == MediaType.VIDEO ? "VIDEO" : "IMAGE");
            writer.newLine();
            writer.write(contentType != null ? contentType : "application/octet-stream");
            writer.newLine();
            writer.write(String.valueOf(timestamp));
            writer.newLine();
            writer.flush();

            return true;

        } catch (IOException e) {
            return false;
        } finally {
            closeStream(writer);
            closeStream(osw);
            closeStream(fos);
        }
    }

    /**
     * Get entry from disk cache
     * @param url The URL key
     * @return CacheEntry or null if not found/expired
     */
    public CacheEntry get(String url) {
        synchronized (lock) {
            String key = getKeyForUrl(url);
            DiskEntry diskEntry = entryIndex.get(key);

            if (diskEntry == null) {
                return null;
            }

            File file = diskEntry.file;
            if (!file.exists()) {
                entryIndex.remove(key);
                return null;
            }

            // Check expiration
            if (diskEntry.isExpired(maxAgeMillis)) {
                removeLocked(key);
                return null;
            }

            // Read file content
            byte[] data = readFileToBytes(file);
            if (data == null) {
                return null;
            }

            return new CacheEntry(url, diskEntry.mediaType, data, diskEntry.contentType);
        }
    }

    /**
     * Put entry to disk cache
     * @param entry The cache entry
     */
    public void put(CacheEntry entry) {
        if (entry == null || entry.getData() == null) {
            return;
        }

        synchronized (lock) {
            String key = getKeyForUrl(entry.getUrl());

            // Check if we need to free space
            while (currentSize + entry.getSize() > maxSizeBytes && !entryIndex.isEmpty()) {
                removeOldestLocked();
            }

            File file = new File(cacheDir, key + ".cache");
            File metaFile = new File(cacheDir, key + ".meta");
            long timestamp = System.currentTimeMillis();

            // Write data file
            boolean dataWritten = writeBytesToFile(file, entry.getData());

            // Write metadata file
            if (dataWritten) {
                writeMetaFile(metaFile, entry.getMediaType(), entry.getContentType(), timestamp);
            }

            if (dataWritten) {
                // Remove old entry if exists
                DiskEntry old = entryIndex.remove(key);
                if (old != null) {
                    currentSize -= old.size;
                    // Delete old meta file
                    if (old.metaFile != null && old.metaFile.exists()) {
                        old.metaFile.delete();
                    }
                }

                DiskEntry diskEntry = new DiskEntry(key, file, metaFile, entry.getSize(), timestamp);
                diskEntry.mediaType = entry.getMediaType();
                diskEntry.contentType = entry.getContentType();

                entryIndex.put(key, diskEntry);
                currentSize += entry.getSize();
            }
        }
    }

    /**
     * Remove entry from disk cache
     * @param url The URL key
     */
    public boolean remove(String url) {
        synchronized (lock) {
            return removeLocked(getKeyForUrl(url));
        }
    }

    /**
     * Internal remove with lock held
     */
    private boolean removeLocked(String key) {
        DiskEntry entry = entryIndex.remove(key);

        if (entry != null) {
            currentSize -= entry.size;
            boolean deleted = entry.file.delete();
            // Also delete meta file
            if (entry.metaFile != null && entry.metaFile.exists()) {
                entry.metaFile.delete();
            }
            return deleted;
        }
        return false;
    }

    /**
     * Remove oldest entry to free space
     */
    private void removeOldestLocked() {
        DiskEntry oldest = null;
        String oldestKey = null;

        for (Map.Entry<String, DiskEntry> e : entryIndex.entrySet()) {
            if (oldest == null || e.getValue().timestamp < oldest.timestamp) {
                oldest = e.getValue();
                oldestKey = e.getKey();
            }
        }

        if (oldestKey != null) {
            removeLocked(oldestKey);
        }
    }

    /**
     * Clear all disk cache
     */
    public void clear() {
        synchronized (lock) {
            for (DiskEntry entry : entryIndex.values()) {
                entry.file.delete();
                if (entry.metaFile != null && entry.metaFile.exists()) {
                    entry.metaFile.delete();
                }
            }
            entryIndex.clear();
            currentSize = 0;
        }
    }

    /**
     * Check if URL exists in cache
     */
    public boolean contains(String url) {
        synchronized (lock) {
            String key = getKeyForUrl(url);
            DiskEntry entry = entryIndex.get(key);
            if (entry == null) {
                return false;
            }
            return !entry.isExpired(maxAgeMillis);
        }
    }

    /**
     * Get cache size
     */
    public long getSize() {
        synchronized (lock) {
            return currentSize;
        }
    }

    /**
     * Get number of entries
     */
    public int getEntryCount() {
        synchronized (lock) {
            return entryIndex.size();
        }
    }

    /**
     * Remove expired entries
     */
    public int removeExpired() {
        synchronized (lock) {
            int removed = 0;
            String[] keys = entryIndex.keySet().toArray(new String[entryIndex.size()]);

            for (String key : keys) {
                DiskEntry entry = entryIndex.get(key);
                if (entry != null && entry.isExpired(maxAgeMillis)) {
                    removeLocked(key);
                    removed++;
                }
            }
            return removed;
        }
    }

    /**
     * Get MD5 hash for URL key
     */
    private String getKeyForUrl(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return Integer.toHexString(url.hashCode());
        }
    }

    /**
     * Read file to byte array
     */
    private byte[] readFileToBytes(File file) {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        ByteArrayOutputStream bos = null;

        try {
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }

            return bos.toByteArray();

        } catch (IOException e) {
            return null;
        } finally {
            closeStream(bis);
            closeStream(fis);
            closeStream(bos);
        }
    }

    /**
     * Write byte array to file
     */
    private boolean writeBytesToFile(File file, byte[] data) {
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        try {
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(data);
            bos.flush();
            fos.getFD().sync();
            return true;

        } catch (IOException e) {
            return false;
        } finally {
            closeStream(bos);
            closeStream(fos);
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

    /**
     * Get cache directory
     */
    public File getCacheDir() {
        return cacheDir;
    }

    /**
     * Disk entry metadata
     */
    private static class DiskEntry {
        String key;
        File file;
        File metaFile;
        long size;
        long timestamp;
        MediaType mediaType;
        String contentType;

        DiskEntry(String key, File file, File metaFile, long size, long timestamp) {
            this.key = key;
            this.file = file;
            this.metaFile = metaFile;
            this.size = size;
            this.timestamp = timestamp;
            this.mediaType = MediaType.IMAGE;
            this.contentType = "application/octet-stream";
        }

        boolean isExpired(long maxAgeMillis) {
            return (System.currentTimeMillis() - timestamp) > maxAgeMillis;
        }
    }
}
