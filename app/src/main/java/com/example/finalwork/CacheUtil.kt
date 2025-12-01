@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.finalwork

import android.app.ActivityManager
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object CacheUtil {
    private var simpleCache: SimpleCache? = null

    // 根据设备内存动态调整缓存大小
    private fun getOptimalCacheSize(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowMemory = activityManager.isLowRamDevice
        // 🆕 增加缓存大小：低内存 100MB，正常设备 200MB
        return if (isLowMemory) 100L * 1024 * 1024 else 200L * 1024 * 1024
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheFolder = File(context.cacheDir, "media")
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(getOptimalCacheSize(context))
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheFolder, cacheEvictor, databaseProvider)
        }
        return simpleCache!!
    }

    fun releaseCache() {
        simpleCache?.release()
        simpleCache = null
    }
}

