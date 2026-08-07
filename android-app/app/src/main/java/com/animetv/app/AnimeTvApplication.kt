package com.animetv.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

class AnimeTvApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure global high-performance Coil ImageLoader with Memory & Disk caching
        val imageLoader = ImageLoader.Builder(this)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // Use 20% of RAM to prevent OutOfMemory crashes on TV devices
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024L * 1024L) // 250 MB disk cache
                    .build()
            }
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
