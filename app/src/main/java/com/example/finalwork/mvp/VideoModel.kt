@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.finalwork.mvp

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.example.finalwork.CacheUtil
import com.example.finalwork.VideoItem
import com.example.finalwork.VideoRepository
import kotlinx.coroutines.*

/**
 * Model 层 - 数据管理
 * 负责：数据加载、预加载、缓存管理
 */
class VideoModel(private val context: Context) {

    private val repository = VideoRepository()
    private val cache = CacheUtil.getCache(context)
    private val preloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val preloadJobs = mutableMapOf<String, Job>()

    private var lastPreloadPosition = -1

    private val upstreamFactory = DefaultDataSource.Factory(context)
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    var enablePreload: Boolean = true

    companion object {
        private const val TAG = "VideoModel"
        private const val PRELOAD_AHEAD_COUNT = 1
        private const val PRELOAD_SIZE_BYTES = 1 * 1024 * 1024L
        private const val CACHE_HIT_THRESHOLD = 512 * 1024L
    }

    private fun buildCacheKey(videoUrl: String): String {
        return if (videoUrl.startsWith("http")) {
            videoUrl
        } else {
            val videoId = context.resources.getIdentifier(videoUrl, "raw", context.packageName)
            "android.resource://${context.packageName}/$videoId"
        }
    }

    fun loadInitialVideos(): List<VideoItem> {
        Log.d(TAG, "加载初始视频列表")
        return repository.loadInitial()
    }

    fun loadMoreVideos(): List<VideoItem> {
        Log.d(TAG, "加载更多视频")
        return repository.loadMore()
    }

    fun preloadNextVideos(currentPosition: Int, totalVideos: Int, videos: List<VideoItem>) {
        if (!enablePreload) {
            Log.d(TAG, "⚠ 预加载已禁用")
            return
        }

        if (currentPosition == lastPreloadPosition) {
            return
        }

        lastPreloadPosition = currentPosition
        cancelPreloadJobs()

        val preloadEndPosition = (currentPosition + PRELOAD_AHEAD_COUNT).coerceAtMost(totalVideos - 1)
        Log.d(TAG, "📹 当前视频 #$currentPosition → 预加载下一个视频 #${currentPosition + 1} (1MB)")

        for (i in currentPosition + 1..preloadEndPosition) {
            if (i >= videos.size) continue

            val videoUrl = videos[i].videoUrl

            // --- 关键修复点 ---
            // 只有当 videoUrl 不为 null (即当前项是视频) 时，才执行预加载逻辑
            if (videoUrl != null) {
                val cacheKey = buildCacheKey(videoUrl)

                if (isCached(cacheKey)) {
                    Log.d(TAG, "  ✓ #$i 已缓存，跳过")
                    continue
                }

                val job = preloadScope.launch {
                    try {
                        Log.d(TAG, "  ⬇ #$i 开始预加载 1MB...")
                        preloadSingleVideo(cacheKey)
                        Log.d(TAG, "  ✅ #$i 预加载完成")
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            Log.d(TAG, "  ❌ #$i 预加载被取消")
                        } else {
                            Log.e(TAG, "  ❌ #$i 预加载失败: ${e.message}")
                        }
                    }
                }
                preloadJobs[cacheKey] = job
            } else {
                // 如果是图片项，打印日志并跳过
                Log.d(TAG, "  ✓ #$i 是图片项，跳过预加载")
            }
            // --- 修复结束 ---
        }
    }

    private suspend fun preloadSingleVideo(cacheKey: String) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(cacheKey)
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(0)
                    .setLength(PRELOAD_SIZE_BYTES)
                    .build()

                val dataSource = cacheDataSourceFactory.createDataSource()

                val cacheWriter = CacheWriter(
                    dataSource,
                    dataSpec,
                    null
                ) { _, bytesCached, _ ->
                    val progress = (bytesCached * 100 / PRELOAD_SIZE_BYTES).toInt()
                    if (progress % 25 == 0) {
                        Log.d(TAG, "预加载进度: $progress%")
                    }
                }

                cacheWriter.cache()
                Log.d(TAG, "✓ 缓存写入完成: $cacheKey")
            } catch (e: Exception) {
                Log.e(TAG, "预加载失败: ${e.message}", e)
                throw e
            }
        }
    }

    fun isCached(cacheKey: String): Boolean {
        return try {
            val cachedBytes = cache.getCachedBytes(cacheKey, 0, PRELOAD_SIZE_BYTES)
            val isCached = cachedBytes >= CACHE_HIT_THRESHOLD
            Log.d(TAG, "检查缓存 $cacheKey: ${if (isCached) "已缓存" else "未缓存"} ($cachedBytes bytes, 阈值: $CACHE_HIT_THRESHOLD)")
            isCached
        } catch (e: Exception) {
            Log.e(TAG, "检查缓存失败: ${e.message}")
            false
        }
    }

    private fun cancelPreloadJobs() {
        if (preloadJobs.isEmpty()) return
        Log.d(TAG, "取消 ${preloadJobs.size} 个预加载任务")
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
    }

    fun cleanup() {
        cancelPreloadJobs()
        preloadScope.cancel()
        Log.d(TAG, "Model 资源已释放")
    }
}
