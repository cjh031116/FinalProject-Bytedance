@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.finalwork

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalwork.databinding.ItemVideoBinding
import kotlinx.coroutines.*

class VideoFeedAdapter(
    private val context: Context,
    private val enablePreload: Boolean = true // 预加载开关，默认开启
) : ListAdapter<VideoItem, VideoFeedAdapter.VideoViewHolder>(VideoDiffCallback()) {

    private var player: ExoPlayer
    private var currentPlayingPosition = RecyclerView.NO_POSITION
    private var currentPlayingHolder: VideoViewHolder? = null

    private val cacheDataSourceFactory: CacheDataSource.Factory
    private val preloadJobs = mutableMapOf<String, Job>()
    private val adapterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 性能统计
    private val loadMetrics = mutableListOf<LoadMetric>()
    private var testStartTime = System.currentTimeMillis()

    companion object {
        private const val PRELOAD_AHEAD_COUNT = 2 // 预加载未来2个视频
        private const val PRELOAD_SIZE_BYTES = 2 * 1024 * 1024L // 预加载2MB
        private const val TAG = "VideoPerformance"
    }

    data class LoadMetric(
        val position: Int,
        val loadTimeMs: Long,
        val isCached: Boolean,
        val timestamp: Long
    )

    init {
        val simpleCache = CacheUtil.getCache(context)
        val upstreamFactory = DefaultDataSource.Factory(context)
        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(cacheDataSourceFactory))
            .build()

        Log.d(TAG, "========================================")
        Log.d(TAG, "VideoFeedAdapter 初始化")
        Log.d(TAG, "预加载功能: ${if (enablePreload) "已启用" else "已禁用"}")
        Log.d(TAG, "测试开始时间: ${System.currentTimeMillis()}")
        Log.d(TAG, "========================================")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        if (holder == currentPlayingHolder) {
            holder.binding.playerView.player = null
        }
    }

    fun playVideoAt(position: Int, recyclerView: RecyclerView) {
        if (position == RecyclerView.NO_POSITION || position == currentPlayingPosition) {
            return
        }

        currentPlayingHolder?.binding?.playerView?.player = null

        val newHolder = recyclerView.findViewHolderForAdapterPosition(position) as? VideoViewHolder
        if (newHolder != null) {
            currentPlayingPosition = position
            currentPlayingHolder = newHolder

            newHolder.binding.playerView.player = player

            val videoUrl = getItem(position).videoUrl
            val mediaItem = buildMediaItem(videoUrl)

            // 记录开始加载时间
            val startTime = System.currentTimeMillis()
            val isCached = checkIfCached(videoUrl)

            Log.d(TAG, "--------------------------------------")
            Log.d(TAG, "准备播放视频 #$position")
            Log.d(TAG, "视频URL: $videoUrl")
            Log.d(TAG, "缓存状态: ${if (isCached) "已缓存" else "未缓存"}")
            Log.d(TAG, "预加载状态: ${if (enablePreload) "开启" else "关闭"}")

            // 添加播放器监听器来测量加载时间
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            Log.d(TAG, "视频 #$position 缓冲中...")
                        }
                        Player.STATE_READY -> {
                            val loadTime = System.currentTimeMillis() - startTime
                            val metric = LoadMetric(position, loadTime, isCached, System.currentTimeMillis())
                            loadMetrics.add(metric)

                            Log.d(TAG, "✓ 视频 #$position 加载完成")
                            Log.d(TAG, "加载耗时: ${loadTime}ms")
                            Log.d(TAG, "是否命中缓存: ${if (isCached) "是" else "否"}")

                            // 输出统计摘要
                            printStatistics()

                            player.removeListener(this)
                        }
                    }
                }
            })

            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            player.playWhenReady = true

            // 触发新的预加载任务
            preloadNextVideos(position)
        }
    }

    private fun preloadNextVideos(currentPosition: Int) {
        if (!enablePreload) {
            Log.d(TAG, "⚠ 预加载已禁用，跳过预加载任务")
            return
        }

        // 1. 取消所有正在进行的预加载任务
        cancelPreloadJobs()

        // 2. 启动新的预加载任务
        val preloadEndPosition = (currentPosition + PRELOAD_AHEAD_COUNT).coerceAtMost(itemCount - 1)

        Log.d(TAG, "开始预加载: 位置 ${currentPosition + 1} 到 $preloadEndPosition")

        for (i in currentPosition + 1..preloadEndPosition) {
            if (i >= itemCount) continue // 越界检查
            val videoUrl = getItem(i).videoUrl
            val jobKey = videoUrl

            val job = adapterScope.launch {
                try {
                    Log.d(TAG, "→ 预加载视频 #$i: $videoUrl")
                    preloadSingleVideo(videoUrl)
                    Log.d(TAG, "✓ 视频 #$i 预加载完成")
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        Log.d(TAG, "✗ 视频 #$i 预加载被取消")
                    } else {
                        Log.e(TAG, "✗ 视频 #$i 预加载失败: ${e.message}")
                    }
                }
            }
            preloadJobs[jobKey] = job
        }
    }

    private suspend fun preloadSingleVideo(videoUrl: String) {
        val mediaItem = buildMediaItem(videoUrl)
        val dataSpec = DataSpec.Builder()
            .setUri(mediaItem.localConfiguration!!.uri)
            .setLength(PRELOAD_SIZE_BYTES) // 3. 限制预加载大小
            .setKey(mediaItem.mediaId) // 使用 mediaId 作为缓存的 key
            .build()

        val cacheWriter = CacheWriter(
            cacheDataSourceFactory.createDataSource(),
            dataSpec,
            null
        ) { _, bytesCached, _ ->
            Log.d("Cache", "Preloading ${mediaItem.mediaId}: $bytesCached / $PRELOAD_SIZE_BYTES bytes.")
        }
        // cache() 是一个阻塞调用，因此它在协程中运行
        withContext(Dispatchers.IO) {
            cacheWriter.cache()
        }
    }

    private fun cancelPreloadJobs() {
        if (preloadJobs.isEmpty()) return
        Log.d("Cache", "Cancelling ${preloadJobs.size} preload jobs.")
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
    }

    private fun buildMediaItem(videoUrl: String): MediaItem {
        val uri = if (videoUrl.startsWith("http")) {
            Uri.parse(videoUrl)
        } else {
            val videoId = context.resources.getIdentifier(videoUrl, "raw", context.packageName)
            Uri.parse("android.resource://${context.packageName}/$videoId")
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(videoUrl) // 使用URL作为唯一ID
            .build()
    }

    // 检查视频是否已缓存
    private fun checkIfCached(videoUrl: String): Boolean {
        return try {
            val simpleCache = CacheUtil.getCache(context)
            val mediaItem = buildMediaItem(videoUrl)
            val uri = mediaItem.localConfiguration?.uri ?: return false
            val cachedBytes = simpleCache.getCachedBytes(uri.toString(), 0, PRELOAD_SIZE_BYTES)
            cachedBytes >= PRELOAD_SIZE_BYTES
        } catch (e: Exception) {
            false
        }
    }

    // 打印统计信息
    private fun printStatistics() {
        if (loadMetrics.isEmpty()) return

        val totalCount = loadMetrics.size
        val avgLoadTime = loadMetrics.map { it.loadTimeMs }.average()
        val cachedMetrics = loadMetrics.filter { it.isCached }
        val nonCachedMetrics = loadMetrics.filter { !it.isCached }
        val cacheHitRate = if (totalCount > 0) (cachedMetrics.size.toFloat() / totalCount * 100) else 0f

        val avgCachedTime = if (cachedMetrics.isNotEmpty()) cachedMetrics.map { it.loadTimeMs }.average() else 0.0
        val avgNonCachedTime = if (nonCachedMetrics.isNotEmpty()) nonCachedMetrics.map { it.loadTimeMs }.average() else 0.0

        Log.d(TAG, "")
        Log.d(TAG, "====== 性能统计摘要 ======")
        Log.d(TAG, "预加载状态: ${if (enablePreload) "开启" else "关闭"}")
        Log.d(TAG, "已播放视频数: $totalCount")
        Log.d(TAG, "平均加载时间: ${avgLoadTime.toInt()}ms")
        Log.d(TAG, "缓存命中率: ${"%.1f".format(cacheHitRate)}%")
        Log.d(TAG, "缓存视频平均加载: ${avgCachedTime.toInt()}ms (${cachedMetrics.size}次)")
        Log.d(TAG, "非缓存视频平均加载: ${avgNonCachedTime.toInt()}ms (${nonCachedMetrics.size}次)")
        Log.d(TAG, "==========================")
        Log.d(TAG, "")
    }

    // 获取完整测试报告
    fun getPerformanceReport(): String {
        if (loadMetrics.isEmpty()) {
            return "暂无数据"
        }

        val totalCount = loadMetrics.size
        val avgLoadTime = loadMetrics.map { it.loadTimeMs }.average()
        val cachedMetrics = loadMetrics.filter { it.isCached }
        val nonCachedMetrics = loadMetrics.filter { !it.isCached }
        val cacheHitRate = (cachedMetrics.size.toFloat() / totalCount * 100)

        val avgCachedTime = if (cachedMetrics.isNotEmpty()) cachedMetrics.map { it.loadTimeMs }.average() else 0.0
        val avgNonCachedTime = if (nonCachedMetrics.isNotEmpty()) nonCachedMetrics.map { it.loadTimeMs }.average() else 0.0

        val minLoadTime = loadMetrics.minOf { it.loadTimeMs }
        val maxLoadTime = loadMetrics.maxOf { it.loadTimeMs }

        val testDuration = System.currentTimeMillis() - testStartTime

        return """
            |
            |========================================
            |           性能测试报告
            |========================================
            |预加载状态: ${if (enablePreload) "✓ 已启用" else "✗ 已禁用"}
            |测试时长: ${testDuration / 1000}秒
            |----------------------------------------
            |播放统计:
            |  总播放次数: $totalCount
            |  平均加载时间: ${avgLoadTime.toInt()}ms
            |  最快加载: ${minLoadTime}ms
            |  最慢加载: ${maxLoadTime}ms
            |----------------------------------------
            |缓存统计:
            |  缓存命中率: ${"%.1f".format(cacheHitRate)}%
            |  缓存命中次数: ${cachedMetrics.size}
            |  缓存未命中次数: ${nonCachedMetrics.size}
            |  缓存视频平均加载: ${avgCachedTime.toInt()}ms
            |  非缓存视频平均加载: ${avgNonCachedTime.toInt()}ms
            |----------------------------------------
            |详细数据:
            |${loadMetrics.joinToString("\n") { 
                "  视频#${it.position}: ${it.loadTimeMs}ms (${if (it.isCached) "缓存" else "网络"})"
            }}
            |========================================
        """.trimMargin()
    }

    fun pauseCurrent() {
        player.pause()
    }

    fun release() {
        cancelPreloadJobs()
        adapterScope.cancel() // 取消整个协程作用域
        player.release()
    }

    fun appendList(newList: List<VideoItem>) {
        submitList(currentList + newList)
    }

    class VideoViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VideoItem) {
            binding.ivAvatar.setImageResource(R.mipmap.ic_launcher_round)
            binding.tvAuthor.text = item.authorName
            binding.tvTitle.text = item.title
            binding.tvLikeCount.text = item.likeCount.toString()
            binding.tvCommentCount.text = item.commentCount.toString()
            binding.tvCollectCount.text = item.collectCount.toString()
            binding.tvShareCount.text = item.shareCount.toString()
        }
    }
}

class VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem == newItem
    }
}
