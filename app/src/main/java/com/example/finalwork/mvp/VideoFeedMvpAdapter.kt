@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.finalwork.mvp

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalwork.CacheUtil
import com.example.finalwork.R
import com.example.finalwork.VideoItem
import com.example.finalwork.databinding.ItemVideoBinding

/**
 * MVP 版本的 Adapter
 * 职责：只负责视图绑定和播放控制，不处理业务逻辑
 */
class VideoFeedMvpAdapter(
    private val context: Context,
    private val onVideoLoadComplete: (position: Int, loadTimeMs: Long, isCached: Boolean) -> Unit
) : ListAdapter<VideoItem, VideoFeedMvpAdapter.VideoViewHolder>(VideoDiffCallback()) {

    private var player: ExoPlayer
    private var currentPlayingPosition = RecyclerView.NO_POSITION
    private var currentPlayingHolder: VideoViewHolder? = null
    private val cacheDataSourceFactory: CacheDataSource.Factory

    companion object {
        private const val TAG = "VideoMvpAdapter"
    }

    init {
        val simpleCache = CacheUtil.getCache(context)
        val upstreamFactory = DefaultDataSource.Factory(context)
        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 🆕 极致优化的缓冲策略
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,   // minBufferMs: 最小缓冲 1 秒
                5000,   // maxBufferMs: 最大缓冲 5 秒
                200,    // bufferForPlaybackMs: 200ms 即可播放（极速起播）
                400     // bufferForPlaybackAfterRebufferMs: 重新缓冲后 400ms
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(-1)  // 🆕 不限制目标缓冲大小
            .build()

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()

        // 🆕 设置播放器为低延迟模式
        player.setVideoScalingMode(androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)

        Log.d(TAG, "Adapter 初始化完成（极致优化）")
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

    /**
     * 播放指定位置的视频
     */
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

            val videoItem = getItem(position)
            val videoUrl = videoItem.videoUrl
            val mediaItem = buildMediaItem(videoUrl)

            // 记录开始加载时间
            val startTime = System.currentTimeMillis()
            val isCached = checkIfCached(videoUrl)

            Log.d(TAG, "开始播放视频 #$position: ${videoItem.title}")
            Log.d(TAG, "缓存状态: ${if (isCached) "已缓存" else "未缓存"}")

            // 添加播放器监听器
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            Log.d(TAG, "视频 #$position 缓冲中...")
                        }
                        Player.STATE_READY -> {
                            val loadTime = System.currentTimeMillis() - startTime

                            // 回调到 Presenter
                            onVideoLoadComplete(position, loadTime, isCached)

                            player.removeListener(this)
                        }
                    }
                }
            })

            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.prepare()
            player.playWhenReady = true
        }
    }

    /**
     * 暂停当前视频
     */
    fun pauseCurrent() {
        player.pause()
    }

    /**
     * 释放播放器资源
     */
    fun release() {
        player.release()
        Log.d(TAG, "播放器资源已释放")
    }

    /**
     * 构建 MediaItem（修复：使用统一的 Cache Key）
     */
    private fun buildMediaItem(videoUrl: String): MediaItem {
        // 🆕 使用统一的 Cache Key
        val cacheKey = buildCacheKey(videoUrl)
        val uri = Uri.parse(cacheKey)

        Log.d(TAG, "构建 MediaItem: $videoUrl -> $cacheKey")

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(cacheKey)  // 🆕 使用 cacheKey 作为 mediaId，保持一致
            .build()
    }

    /**
     * 检查视频是否已缓存（修复：使用统一的 Cache Key）
     */
    private fun checkIfCached(videoUrl: String): Boolean {
        return try {
            val simpleCache = CacheUtil.getCache(context)

            // 🆕 使用与 Model 相同的 Cache Key 生成逻辑
            val cacheKey = buildCacheKey(videoUrl)

            val preloadSize = 2 * 1024 * 1024L  // 预加载 2MB
            val cacheHitThreshold = 1024 * 1024L  // 缓存命中阈值：1MB
            val cachedBytes = simpleCache.getCachedBytes(cacheKey, 0, preloadSize)
            val isCached = cachedBytes >= cacheHitThreshold

            Log.d(TAG, "🔍 检查缓存: $videoUrl -> ${if (isCached) "✅ 已缓存" else "❌ 未缓存"} ($cachedBytes / $cacheHitThreshold bytes)")

            isCached
        } catch (e: Exception) {
            Log.e(TAG, "缓存检查失败: ${e.message}")
            false
        }
    }

    /**
     * 🆕 生成统一的 Cache Key（与 VideoModel 保持一致）
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
     * ViewHolder
     */
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

/**
 * DiffUtil 回调
 */
class VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem == newItem
    }
}

