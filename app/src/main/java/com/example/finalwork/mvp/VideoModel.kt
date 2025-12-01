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

    // 🆕 记录最后预加载的位置，避免重复触发
    private var lastPreloadPosition = -1

    // 🆕 预加载需要的组件
    private val upstreamFactory = DefaultDataSource.Factory(context)
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    var enablePreload: Boolean = true

    companion object {
        private const val TAG = "VideoModel"
        private const val PRELOAD_AHEAD_COUNT = 2  // 预加载 2 个视频
        private const val PRELOAD_SIZE_BYTES = 2 * 1024 * 1024L  // 预加载 2MB
        private const val CACHE_HIT_THRESHOLD = 1024 * 1024L  // 🆕 缓存命中阈值：1MB 即可
    }

    /**
     * 🆕 将 videoUrl 转换为统一的 URI 字符串（与 Adapter 保持一致）
     */
    private fun buildCacheKey(videoUrl: String): String {
        return if (videoUrl.startsWith("http")) {
            videoUrl
        } else {
            val videoId = context.resources.getIdentifier(videoUrl, "raw", context.packageName)
            "android.resource://${context.packageName}/$videoId"
        }
    }

    /**
     * 加载初始视频列表
     */
    fun loadInitialVideos(): List<VideoItem> {
        Log.d(TAG, "加载初始视频列表")
        return repository.loadInitial()
    }

    /**
     * 加载更多视频
     */
    fun loadMoreVideos(): List<VideoItem> {
        Log.d(TAG, "加载更多视频")
        return repository.loadMore()
    }

    /**
     * 预加载接下来的视频
     */
    fun preloadNextVideos(currentPosition: Int, totalVideos: Int, videos: List<VideoItem>) {
        if (!enablePreload) {
            Log.d(TAG, "⚠ 预加载已禁用")
            return
        }

        // 🆕 如果位置没变，跳过（避免重复预加载）
        if (currentPosition == lastPreloadPosition) {
            return  // 静默跳过，不打印日志
        }

        lastPreloadPosition = currentPosition

        // 取消之前的预加载任务
        cancelPreloadJobs()

        val preloadEndPosition = (currentPosition + PRELOAD_AHEAD_COUNT).coerceAtMost(totalVideos - 1)
        Log.d(TAG, "📹 视频 #$currentPosition → 开始预加载 #${currentPosition + 1} 到 #$preloadEndPosition")

        for (i in currentPosition + 1..preloadEndPosition) {
            if (i >= videos.size) continue

            val videoUrl = videos[i].videoUrl
            val cacheKey = buildCacheKey(videoUrl)

            // 检查是否已缓存
            if (isCached(cacheKey)) {
                Log.d(TAG, "  ✓ #$i 已缓存，跳过")
                continue
            }

            // 🆕 立即预加载下一个视频，第二个视频稍微延迟
            val job = preloadScope.launch {
                try {
                    // 下一个视频立即预加载，第二个视频延迟 500ms
                    if (i > currentPosition + 1) {
                        delay(500L)
                    }

                    Log.d(TAG, "  ⬇ #$i 开始预加载...")
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
        }
    }

    /**
     * 🆕 真正实现预加载 - 使用 CacheWriter 写入缓存
     */
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

                // 使用 CacheWriter 写入缓存
                val cacheWriter = CacheWriter(
                    dataSource,
                    dataSpec,
                    null
                ) { _, bytesCached, _ ->
                    // 进度回调
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

    /**
     * 检查视频是否已缓存（优化：降低阈值）
     */
    fun isCached(cacheKey: String): Boolean {
        return try {
            val cachedBytes = cache.getCachedBytes(cacheKey, 0, PRELOAD_SIZE_BYTES)
            // 🆕 只要有 1MB 就算缓存命中，提高命中率
            val isCached = cachedBytes >= CACHE_HIT_THRESHOLD
            Log.d(TAG, "检查缓存 $cacheKey: ${if (isCached) "已缓存" else "未缓存"} ($cachedBytes bytes, 阈值: $CACHE_HIT_THRESHOLD)")
            isCached
        } catch (e: Exception) {
            Log.e(TAG, "检查缓存失败: ${e.message}")
            false
        }
    }

    /**
     * 取消所有预加载任务
     */
    private fun cancelPreloadJobs() {
        if (preloadJobs.isEmpty()) return
        Log.d(TAG, "取消 ${preloadJobs.size} 个预加载任务")
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        cancelPreloadJobs()
        preloadScope.cancel()
        Log.d(TAG, "Model 资源已释放")
    }
}

