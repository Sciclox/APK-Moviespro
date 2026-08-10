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
        com.animetv.app.data.api.ApiClient.setCacheDir(cacheDir)

        // Configure global high-performance Coil ImageLoader with Memory & Disk caching backed by ApiClient.okHttpClient
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(com.animetv.app.data.api.ApiClient.okHttpClient)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35) // Use up to 35% of RAM for image cache
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(500L * 1024L * 1024L) // 500 MB disk cache
                    .build()
            }
            .crossfade(100)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
