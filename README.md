# Android Media Caching Library

A production-ready Android library for caching images and videos, compatible with Java 7.

## Features

- Two-tier caching (Memory LRU + Disk cache)
- Callbacks delivered on Main Thread
- Duplicate URL handling
- Progress tracking during download
- Context-based initialization
- Java 7 compatible

## Installation (JitPack)

To use this library in your Android project, add the JitPack repository and the dependency to your `build.gradle` files.

**Step 1. Add the JitPack repository to your root `build.gradle`:**

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2. Add the dependency to your app module's `build.gradle`:**

```gradle
dependencies {
    implementation 'com.github.Omdeepakumar:mediacache:1.0.0'
}
```

## Usage

### Initialization

Initialize the cache helper once in your `Application` class:

```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MediaCacheHelper.init(this);
    }
}
```

### Loading Media

```java
MediaCacheHelper cache = MediaCacheHelper.getInstance();

// Configure cache settings (optional)
cache.setMemoryCacheSize(50);  // 50 items in memory
cache.setDiskCacheSize(100 * 1024 * 1024);  // 100 MB on disk
cache.setDiskCacheDirectory("my_image_cache");

String imageUrl = "https://example.com/image.jpg";

cache.loadMedia(imageUrl, MediaType.IMAGE, new CacheCallback.SimpleCallback() {

    @Override
    public void onSuccess(String url, byte[] data, boolean fromCache) {
        // Update UI on main thread
        // Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        // imageView.setImageBitmap(bitmap);
    }

    @Override
    public void onError(String url, String error) {
        // Handle error on main thread
    }

    @Override
    public void onProgress(String url, long bytesLoaded, long totalBytes) {
        // Update progress on main thread
    }
});
```

For more examples, refer to the `MediaCacheHelperExample.java` file in the source code.

## License

[Specify your license here, e.g., MIT, Apache 2.0]
